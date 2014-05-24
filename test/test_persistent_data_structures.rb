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
      vector = Persistent::Vector[*1..10000]
      assert_equal vector.size , 10000
      assert_equal vector.get(911) , 912
    end

    def test_create
      vector = Persistent::Vector[*1..10000]
      assert_equal vector.size , 10000
      assert_equal vector.get(919) , 920
    end

    def test_vector_add
      vector = Persistent::Vector[]
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      (1..10000).each do |i|
        assert_equal vector.get(i-1) , i
      end
    end

    def test_vector_set
      vector = Persistent::Vector[]
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      new_vector = vector.set(996, 2004)
      assert_equal new_vector.get(996), 2004
    end

    def test_vector_each
      vector = Persistent::Vector[]
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      vector.all? { |i| assert_equal vector[i-1], i }
    end

    def test_vector_map
      vector = Persistent::Vector[]
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      vector = vector.map { |i| i * i}
      assert_equal vector.class.name, 'Persistent::Vector'
      (1..10000).all? { |i| assert_equal i * i, vector[i-1] }
    end

    def test_vector_clear
      vector = Persistent::Vector[]
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      vector = vector.clear
      assert_equal vector.class.name, 'Persistent::Vector'
      assert vector.empty?
    end

    def test_vector_select
      vector = Persistent::Vector[]
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      vector = vector.select { |i| i > 9900 }
      assert_equal vector.class.name, 'Persistent::Vector'
      (1..100).zip(9901..10000).all? { |i, j| assert_equal vector.get(i-1), j }
    end

    def test_vector_reject
      vector = Persistent::Vector[]
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      vector = vector.reject { |i| i <= 9900 }
      assert_equal vector.class.name, 'Persistent::Vector'
      (1..100).zip(9901..10000).all? { |i, j| assert_equal vector.get(i-1), j }
    end

    def test_vector_pop
      vector = Persistent::Vector[]
      (1..10000).each do |i|
        vector = vector.add(i)
      end
      (10000).downto(2).each do |i|
        new_vector = vector.pop
        assert_equal new_vector.tail.last, i-1
        vector = new_vector
      end
    end

    def test_vector_inspect
      vector = Persistent::Vector[1,2,3]
      assert_equal vector.inspect, 'Persistent::Vector[1, 2, 3]'
    end

    def test_vector_equality
      vector = Persistent::Vector[1,2,3]
      assert vector == [1,2,3]
      assert vector != [1,2,3,4]
      assert vector.eql? Persistent::Vector[1,2,3]

      refute vector.eql? [1,2,3]
      refute vector.eql? Persistent::Vector[1,2,3,4]
    end
  end
end
