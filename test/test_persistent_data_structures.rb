# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require 'test/unit'
require 'persistent_data_structure'

module Persistent
  class TestVector < Test::Unit::TestCase
    def test_construct
      vector = Persistent::Vector.vector([*1..10000])
      assert_equal vector.size , 10000
      assert_equal vector.get(919) , 920
    end

    def test_vector_add
      vector = Persistent::Vector.vector([])
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      (1..10000).each do |i|
        assert_equal vector.get(i-1) , i
      end
    end

    def test_vector_set
      vector = Persistent::Vector.vector([])
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      new_vector = vector.set(996, 2004)
      assert_equal new_vector.get(996), 2004
    end

    def test_vector_each
      vector = Persistent::Vector.vector([])
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      vector.each { |i| assert_equal vector[i-1], i }
    end

    def test_vector_map
      vector = Persistent::Vector.vector([])
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      vector = vector.map { |i| i * i}
      assert_equal vector.class.name, 'Persistent::Vector'
      (1..10000).each { |i| assert_equal i * i, vector[i-1] }
    end

    def test_vector_clear
      vector = Persistent::Vector.vector([])
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      vector = vector.clear
      assert_equal vector.class.name, 'Persistent::Vector'
      assert vector.empty?
    end


    def test_vector_pop
      vector = Persistent::Vector.vector([])
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      (10000).downto(2).each do |i|
        new_vector = vector.pop
        assert_equal new_vector.tail.last, i-1
        vector = new_vector
      end
    end
  end
end
