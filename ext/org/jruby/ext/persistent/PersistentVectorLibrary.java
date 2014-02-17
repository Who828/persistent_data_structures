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

public class PersistentVectorLibrary implements Library {
    static public RubyClass Node;
    static public RubyClass PersistentVector;
    static public RubyClass TransientVector;

    public void load(Ruby runtime, boolean wrap) {
        RubyModule persistent = runtime.getOrCreateModule("Persistent");
        RubyClass persistentVector = persistent.defineOrGetClassUnder("Vector", runtime.getObject());
        persistentVector.setAllocator(new ObjectAllocator() {
            @Override
            public IRubyObject allocate(Ruby ruby, RubyClass rubyClass) {
                return new PersistentVector(ruby, rubyClass);
            }
        });
        persistentVector.defineAnnotatedMethods(PersistentVector.class);
    }

    public static class Node extends RubyObject {
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
        static AtomicReference<Thread> NOEDIT = new AtomicReference<Thread>(null);
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

        @JRubyMethod(name = "vector", meta = true)
        static public IRubyObject vector(ThreadContext context, IRubyObject cls, IRubyObject items) {
            PersistentVector ret = new PersistentVector(context.runtime, (RubyClass) cls);
            PersistentVector ret1 = (PersistentVector) ret.initialize(context, 0, 5, new Node(context.runtime, Node).initialize_params(context, NOEDIT), RubyArray.newArray(context.runtime, 32));
            TransientVector ret2 = ret1.asTransient(context);
            for(Object item : (RubyArray) items) {
                ret2 = (TransientVector) ret2.conj(context, JavaUtil.convertJavaToRuby(context.runtime, item));
            }
            return ret2.persistent(context, (RubyClass) cls);
       }

       @JRubyMethod(name = "size")
       public IRubyObject count(ThreadContext context) {
           return JavaUtil.convertJavaToRuby(context.runtime, cnt);
       }

       private static Node newPath(ThreadContext context, AtomicReference<Thread> edit, int level, Node node) {
           if (level == 0)
               return node;
           Node ret = new Node(context.runtime, Node).initialize_params(context, edit);
           ret.array.unshift(newPath(context, edit, level - 5, node));
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
                Node child = (Node) parent.array.at(RubyFixnum.newFixnum(context.runtime, subidx));
                nodeToInsert = (child != null)?
                        pushTail(context, level-5,child, tailnode)
                        :newPath(context, root.edit,level-5, tailnode);
            }
            ret.array.insert(RubyFixnum.newFixnum(context.runtime, subidx), nodeToInsert);
            return ret;
        }

       @JRubyMethod(name = "add", required = 1)
       public IRubyObject add(ThreadContext context, IRubyObject val) {
           if (cnt - tailoff() < 32) {
               PersistentVector ret = new PersistentVector(context.runtime, getMetaClass());
               RubyArray newTail = tail.aryDup();
               newTail.add(val);
               return ret.initialize(context, this.cnt+1, this.shift, this.root, newTail);
           }

           Node newroot;
           Node tailnode = new Node(context.runtime, Node).initialize_params(context, root.edit);
           int newshift = shift;

           if ((cnt >>> 5) > (1 << shift)) {
               newroot = new Node(context.runtime, Node).initialize_params(context, root.edit);
               newroot.array.unshift(newPath(context, root.edit, shift, tailnode));
               newroot.array.unshift(root);
               newshift += 5;
           } else
               newroot = pushTail(context, shift, root, tailnode);

           RubyArray arry = RubyArray.newArray(context.runtime);
           arry.unshift(val);

           return new PersistentVector(context.runtime, getMetaClass()).initialize(context, cnt + 1, newshift, newroot, arry);
       }

        final int tailoff(){
            if (cnt < 32)
                return 0;
            return ((cnt-1) >>> 5) << 5;
        }


    }

    public static class TransientVector extends RubyObject {
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
            ret.array.unshift(newPath(context, edit, level - 5, node));
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
                Node child = (Node) parent.array.at(RubyFixnum.newFixnum(context.runtime, subidx));
                nodeToInsert = (child != null)?
                        pushTail(context, level-5,child, tailnode)
                        :newPath(context, root.edit,level-5, tailnode);
            }
            ret.array.insert(RubyFixnum.newFixnum(context.runtime, subidx), nodeToInsert);
            return ret;
        }

        public PersistentVector persistent(ThreadContext context, RubyClass cls){
            ensureEditable();
            Ruby runtime = context.runtime;
            root.edit.set(null);
            RubyArray trimmedTail = (RubyArray) tail.slice_bang(RubyFixnum.newFixnum(runtime, 0), RubyFixnum.newFixnum(runtime, cnt-tailoff())).dup();
            return (PersistentVector) new PersistentVector(context.runtime, cls).initialize(context, cnt, shift, root, trimmedTail);
        }

        public IRubyObject conj(ThreadContext context, IRubyObject val) {
            ensureEditable();
            int i = cnt;

            if (i - tailoff() < 32) {
                tail.insert(RubyFixnum.newFixnum(context.runtime, i & 0x01f), val);
                ++cnt;
                return this;
            }

            Node newroot;
            Node tailnode = new Node(context.runtime, Node).initialize_params_arry(context, root.edit, tail);
            tail = RubyArray.newArray(context.runtime, 32);
            tail.unshift(val);
            int newshift = shift;

            if ((cnt >>> 5) > (1 << shift)) {
                newroot = new Node(context.runtime, Node).initialize_params(context, root.edit);
                newroot.array.unshift(newPath(context, root.edit, shift, tailnode));
                newroot.array.unshift(root);
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
