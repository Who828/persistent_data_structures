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

        @JRubyMethod(name = "vector", meta = true)
        static public IRubyObject vector(ThreadContext context, IRubyObject cls, IRubyObject items) {
            PersistentVector ret = new PersistentVector(context.runtime, (RubyClass) cls);
            PersistentVector ret1 = (PersistentVector) ret.initialize(context, 0, 5, new Node(context.runtime, Node).initialize_params(context, NOEDIT), RubyArray.newArray(context.runtime));
            for(Object item : (RubyArray) items) {
                ret1 = (PersistentVector) ret1.add(context, JavaUtil.convertJavaToRuby(context.runtime, item));
            }
            return ret1;
       }

       @JRubyMethod(name = "tail")
       public IRubyObject tail(ThreadContext context) {
           return this.tail;
       }

       private static Node newPath(ThreadContext context, AtomicReference<Thread> edit, int level, Node node) {
           if (level == 0)
               return node;
           Node ret = new Node(context.runtime, Node).initialize_params(context, edit);
           ret.array.set(0, newPath(context, edit, level-5, node));
           return  ret;
       }

        private Node pushTail(ThreadContext context, int level, Node parent, Node tailnode){
            int subidx = ((cnt - 1) >>> level) & 0x01f;
            Node ret = new Node(context.runtime, Node).initialize_params_arry(context, parent.edit, (RubyArray) parent.array.dup());
            Node nodeToInsert;
            if(level == 5)
            {
                nodeToInsert = tailnode;
            }
            else
            {
                Node child = (Node) parent.array.get(subidx);
                nodeToInsert = (child != null)?
                        pushTail(context, level-5,child, tailnode)
                        :newPath(context, root.edit,level-5, tailnode);
            }
            ret.array.set(subidx, nodeToInsert);
            return ret;
        }

       @JRubyMethod(name = "add", required = 1)
       public IRubyObject add(ThreadContext context, IRubyObject val) {
           if (cnt - tailoff() < 32) {
               PersistentVector ret = new PersistentVector(context.runtime, getMetaClass());
               RubyArray newTail = (RubyArray) tail.dup();
               newTail.add(val);
               return ret.initialize(context, this.cnt+1, this.shift, this.root, newTail);
           }

           Node newroot;
           Node tailnode = new Node(context.runtime, Node).initialize_params(context, root.edit);
           int newshift = shift;

           if ((cnt >> 5) > (1 << shift)) {
               System.out.println("level" + shift);
               newroot = new Node(context.runtime, Node).initialize_params(context, root.edit);
               newroot.array.set(0, root);
               newroot.array.set(1, newPath(context, root.edit, shift, tailnode));
               newshift += 5;
           } else
               newroot = pushTail(context, shift, root, tailnode);

           RubyArray arry = RubyArray.newArray(context.runtime, 1);
           arry.unshift(val);

           return new PersistentVector(context.runtime, getMetaClass()).initialize(context, cnt + 1, newshift, newroot, arry);
       }

        final int tailoff(){
            if (cnt < 32)
                return 0;
            return ((cnt-1) >>> 5) << 5;
        }


    }

}
