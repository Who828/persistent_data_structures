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
  end
end
