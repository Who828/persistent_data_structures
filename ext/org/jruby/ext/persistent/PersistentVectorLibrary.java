// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.jruby.ext.persistent;

import java.lang.Override;
import java.lang.Thread;
import java.lang.reflect.Field;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jruby.*;
import org.jruby.Ruby;
import org.jruby.RubyArgsFile;
import org.jruby.javasupport.JavaUtil;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.runtime.Block;
import org.jruby.runtime.BlockBody;
import org.jruby.RubyFixnum;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.load.Library;
import java.util.concurrent.atomic.AtomicReference;

import static org.jruby.RubyEnumerator.enumeratorize;

public class PersistentVectorLibrary implements Library {
    static private RubyClass Node;
    static public RubyClass PersistentVector;
    static private RubyClass TransientVector;

    public void load(Ruby runtime, boolean wrap) {
        RubyModule persistent = runtime.getOrCreateModule("Persistent");
        RubyClass persistentVector = persistent.defineOrGetClassUnder("Vector", runtime.getObject());
        persistentVector.setAllocator(new ObjectAllocator() {
            @Override
            public IRubyObject allocate(Ruby ruby, RubyClass rubyClass) {
                return new PersistentVector(ruby, rubyClass);
            }
        });
        persistentVector.includeModule(runtime.getEnumerable());
        persistentVector.defineAnnotatedMethods(PersistentVector.class);
    }

    private static class Node extends RubyObject {
        transient public AtomicReference<Thread> edit;
        public RubyArray array;

        public Node(Ruby runtime, RubyClass rubyClass) {
            super(runtime, rubyClass);
        }

        public Node initialize_params(ThreadContext context, AtomicReference<Thread> edit) {
            this.edit = edit;
            this.array = RubyArray.newArray(context.runtime, 32);
            return this;
        }

        public Node initialize_params_arry(ThreadContext context, AtomicReference<Thread> edit, RubyArray arry) {
            this.edit = edit;
            this.array = arry;
            return this;
        }
    }

    @JRubyClass(name="Vector")
    public static class PersistentVector extends RubyObject {
        static final AtomicReference<Thread> NOEDIT = new AtomicReference<Thread>(null);

        public int cnt;
        public  int shift;
        public  Node root;
        public  RubyArray tail;


        public PersistentVector(Ruby runtime, RubyClass rubyClass) {
            super(runtime, rubyClass);
        }

        public IRubyObject initialize(ThreadContext context, int cnt, int shift, Node root, RubyArray tail) {
            this.cnt = cnt;
            this.shift = shift;
            this.root = root;
            this.tail = tail;
            return this;
        }

        public TransientVector asTransient(ThreadContext context){
            return (TransientVector) new TransientVector(context.runtime, TransientVector).initialize(context, this);
        }

        static private PersistentVector emptyVector(ThreadContext context, RubyClass rubyClass) {
            Node emptyNode =  emptyNode(context);
            return (PersistentVector) new PersistentVector(context.runtime, rubyClass).initialize(context, 0, 5, emptyNode, RubyArray.newArray(context.runtime, 32));
        }

        static private Node emptyNode(ThreadContext context) {
            return new Node(context.runtime, Node).initialize_params(context, NOEDIT);
        }

        @JRubyMethod(name = "vector", meta = true, required=1)
        static public IRubyObject vector(ThreadContext context, IRubyObject cls, IRubyObject items) {
            TransientVector ret = emptyVector(context, (RubyClass) cls).asTransient(context);
            RubyArray item_list = (RubyArray) items;
            for(int i=0; i < item_list.getLength(); i++) {
                ret = (TransientVector) ret.conj(context, item_list.entry(i));
            }
            return ret.persistent(context, (RubyClass) cls);
        }

        @JRubyMethod(name = "size", alias = "length")
        public IRubyObject count(ThreadContext context) {
            return JavaUtil.convertJavaToRuby(context.runtime, cnt);
        }

        @JRubyMethod(name = "tail")
        public IRubyObject tail(ThreadContext context) {
            return tail;
        }

        @JRubyMethod
        public IRubyObject each(ThreadContext context, Block block) {
            if (!block.isGiven()) return enumeratorize(context.runtime, this, "each");

            for(int i=0; i < cnt; i++) {
                block.yield(context, nth(context, RubyFixnum.newFixnum(context.runtime, i)));
            }
            return this;
        }

        @JRubyMethod(name = {"collect", "map"})
        public IRubyObject collect(ThreadContext context, Block block) {
            Ruby runtime = context.runtime;
            if (!block.isGiven()) return emptyVector(context, getMetaClass());

            TransientVector ret = emptyVector(context, getMetaClass()).asTransient(context);

            for (int i = 0; i < cnt; i++) {
               ret = (TransientVector) ret.conj(context, block.yield(context, get(context, i)));
            }

            return ret.persistent(context, getMetaClass());
        }

        @JRubyMethod(name = "clear")
        public IRubyObject clear(ThreadContext context) {
           return emptyVector(context, getMetaClass());
        }

        @JRubyMethod(name = "empty?")
        public IRubyObject isEmpty(ThreadContext context) {
            return RubyBoolean.newBoolean(context.runtime, cnt == 0);
        }

        @JRubyMethod(name = "get", alias = "[]", required=1)
        public IRubyObject nth(ThreadContext context, IRubyObject i) {
            int j = RubyNumeric.num2int(i);
            return get(context, j);
        }


        public IRubyObject get(ThreadContext context, int i) {
            RubyArray node = arrayFor(i);
            return node.entry(i & 0x01f);
        }
        private RubyArray arrayFor(int i){
            if(i >= 0 && i < cnt)
            {
                if(i >= tailoff())
                    return tail;
                Node node = root;
                for(int level = shift; level > 0; level -= 5)
                    node = (Node) node.array.entry((i >>> level) & 0x01f);
                return node.array;
            }
            throw new IndexOutOfBoundsException();
        }

        @JRubyMethod(name = "set", required=2)
        public IRubyObject set(ThreadContext context, IRubyObject i, IRubyObject val) {
            int j = RubyNumeric.num2int(i);
            if (j >=0 && j < cnt) {
                if (j >= tailoff()) {
                    RubyArray newTail = tail.aryDup();
                    newTail.store(j & 0x01f, val);
                    return new PersistentVector(context.runtime, getMetaClass()).initialize(context, cnt, shift, root, newTail);
                }
                return new PersistentVector(context.runtime, getMetaClass()).initialize(context, cnt, shift, doSet(context, shift, root, j, val), tail);
            }

            if (j == cnt)
                add(context, val);

            throw new IndexOutOfBoundsException();
        }

        private Node doSet(ThreadContext context, int level, Node node, int i, IRubyObject val) {
            Node ret = new Node(context.runtime, Node).initialize_params_arry(context, node.edit, node.array.aryDup());
            if (level == 0)
                ret.array.store(i & 0x01f, val);
            else {
                int subidx = (i >> level) & 0x01f;
                ret.array.store(subidx, doSet(context, level-5, (Node) node.array.entry(subidx), i, val));
            }
            return ret;
        }


        private static Node newPath(ThreadContext context, AtomicReference<Thread> edit, int level, Node node) {
            if (level == 0)
                return node;
            Node ret = new Node(context.runtime, Node).initialize_params(context, edit);
            ret.array.store(0, newPath(context, edit, level - 5, node));
            return  ret;
        }

        private Node pushTail(ThreadContext context, int level, Node parent, Node tailnode){
            int subidx = ((cnt - 1) >>> level) & 0x01f;
            Node ret = new Node(context.runtime, Node).initialize_params_arry(context, parent.edit, parent.array.aryDup());
            Node nodeToInsert;
            if(level == 5)
            {
                nodeToInsert = tailnode;
            }
            else
            {
                IRubyObject child = parent.array.entry(subidx);
                nodeToInsert = (!child.isNil())?
                        pushTail(context, level-5,(Node) child, tailnode)
                        :newPath(context, root.edit,level-5, tailnode);
            }
            ret.array.store(subidx, nodeToInsert);
            return ret;
        }

        @JRubyMethod(name = "add", required = 1)
        public IRubyObject add(ThreadContext context, IRubyObject val) {
            if (cnt - tailoff() < 32) {
                PersistentVector ret = new PersistentVector(context.runtime, getMetaClass());
                RubyArray newTail = tail.aryDup();
                newTail.append(val);
                return ret.initialize(context, this.cnt+1, this.shift, this.root, newTail);
            }

            Node newroot;
            Node tailnode = new Node(context.runtime, Node).initialize_params_arry(context, root.edit, tail);
            int newshift = shift;

            if ((cnt >>> 5) > (1 << shift)) {
                newroot = new Node(context.runtime, Node).initialize_params(context, root.edit);
                newroot.array.store(0, root);
                newroot.array.store(1, newPath(context, root.edit, shift, tailnode));
                newshift += 5;
            } else
                newroot = pushTail(context, shift, root, tailnode);

            RubyArray arry = RubyArray.newArray(context.runtime);
            arry.store(0, val);

            return new PersistentVector(context.runtime, getMetaClass()).initialize(context, cnt + 1, newshift, newroot, arry);
        }

        final int tailoff(){
            if (cnt < 32)
                return 0;
            return ((cnt-1) >>> 5) << 5;
        }

        @JRubyMethod(name = "pop")
        public IRubyObject pop(ThreadContext context){
            if(cnt == 0)
                throw new IllegalStateException("Can't pop empty vector");
            if(cnt == 1)
                return emptyVector(context, getMetaClass());
            if(cnt-tailoff() > 1)
            {
                RubyArray newTail = (RubyArray) tail.subseq(0, tail.getLength()-1).dup();
                return new PersistentVector(context.runtime, getMetaClass()).initialize(context, cnt - 1, shift, root, newTail);
            }
            RubyArray newtail = arrayFor(cnt - 2);

            Node newroot = popTail(context, shift, root);
            int newshift = shift;
            if(newroot == null)
            {
                newroot = emptyNode(context);
            }
            if(shift > 5 && newroot.array.entry(1) == null)
            {
                newroot = (Node) newroot.array.entry(0);
                newshift -= 5;
            }
            return new PersistentVector(context.runtime, getMetaClass()).initialize(context, cnt - 1, newshift, newroot, newtail);
        }

        private Node popTail(ThreadContext context, int level, Node node){
            int subidx = ((cnt-2) >>> level) & 0x01f;
            if(level > 5)
            {
                Node newchild = popTail(context, level - 5, (Node) node.array.entry(subidx));
                if(newchild == null && subidx == 0)
                    return null;
                else
                {
                    Node ret = new Node(context.runtime, Node).initialize_params_arry(context, root.edit, node.array.aryDup());
                    ret.array.store(subidx, newchild);
                    return ret;
                }
            }
            else if(subidx == 0)
                return null;
            else
            {
                Node ret = new Node(context.runtime, Node).initialize_params_arry(context, root.edit, node.array.aryDup());
                ret.array.store(subidx, null);
                return ret;
            }
        }
    }

    private static class TransientVector extends RubyObject {
        int cnt;
        int shift;
        Node root;
        RubyArray tail;


        public TransientVector(Ruby runtime, RubyClass rubyClass) {
            super(runtime, rubyClass);
        }

        public IRubyObject initialize(ThreadContext context, PersistentVector v) {
            this.cnt = v.cnt;
            this.shift = v.shift;
            this.root = editableRoot(context, v.root);
            this.tail =  v.tail.aryDup();
            return this;
        }

        static Node editableRoot(ThreadContext context, Node node){
            return new Node(context.runtime, Node).initialize_params_arry(context, new AtomicReference<Thread>(Thread.currentThread()), node.array.aryDup());
        }


        private static Node newPath(ThreadContext context, AtomicReference<Thread> edit, int level, Node node) {
            if (level == 0)
                return node;
            Node ret = new Node(context.runtime, Node).initialize_params(context, edit);
            ret.array.store(0, newPath(context, edit, level - 5, node));
            return  ret;
        }

        void ensureEditable(){
            Thread owner = root.edit.get();
            if(owner == Thread.currentThread())
                return;
            if(owner != null)
                throw new IllegalAccessError("Transient used by non-owner thread");
            throw new IllegalAccessError("Transient used after persistent! call");
        }

        Node ensureEditable(ThreadContext context, Node node){
            if(node.edit == root.edit)
                return node;
            return new Node(context.runtime, Node).initialize_params_arry(context, root.edit, node.array.aryDup());
        }

        private Node pushTail(ThreadContext context, int level, Node parent, Node tailnode){
            parent = ensureEditable(context, parent);
            int subidx = ((cnt - 1) >>> level) & 0x01f;
            Node ret = parent;
            Node nodeToInsert;
            if(level == 5)
            {
                nodeToInsert = tailnode;
            }
            else
            {
                IRubyObject child = parent.array.entry(subidx);
                nodeToInsert = (!child.isNil())?
                        pushTail(context, level-5, (Node) child, tailnode)
                        :newPath(context, root.edit,level-5, tailnode);
            }
            ret.array.store(subidx, nodeToInsert);
            return ret;
        }

        public PersistentVector persistent(ThreadContext context, RubyClass cls){
            ensureEditable();
            Ruby runtime = context.runtime;
            root.edit.set(null);
            RubyArray trimmedTail = (RubyArray) tail.subseq(0,  cnt-tailoff()).dup();
            return (PersistentVector) new PersistentVector(context.runtime, cls).initialize(context, cnt, shift, root, trimmedTail);
        }

        public IRubyObject conj(ThreadContext context, IRubyObject val) {
            ensureEditable();
            int i = cnt;

            if (i - tailoff() < 32) {
                tail.store(i & 0x01f, val);
                ++cnt;
                return this;
            }

            Node newroot;
            Node tailnode = new Node(context.runtime, Node).initialize_params_arry(context, root.edit, tail);
            tail = RubyArray.newArray(context.runtime, 32);
            tail.store(0, val);
            int newshift = shift;

            if ((cnt >>> 5) > (1 << shift)) {
                newroot = new Node(context.runtime, Node).initialize_params(context, root.edit);
                newroot.array.store(0,root);
                newroot.array.store(1, newPath(context, root.edit, shift, tailnode));
                newshift += 5;
            } else
                newroot = pushTail(context, shift, root, tailnode);

            root = newroot;
            shift = newshift;
            ++cnt;
            return this;
        }

        final int tailoff(){
            if (cnt < 32)
                return 0;
            return ((cnt-1) >>> 5) << 5;
        }
    }
}
