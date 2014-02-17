require 'benchmark'
require 'persistent_data_structure'
25.times do 
  arry = [*1..100000]
  puts "Vector from Array"
  puts Benchmark.measure {
    10.times { vector = Persistent::Vector.vector(arry) }
  }
  puts "Vector#add"
  puts Benchmark.measure {
    10.times do
      v = Persistent::Vector.vector([1])
      100000.times { |i| v = v.add(i) }
    end
  }
  puts "Array#<<"
  puts Benchmark.measure {
    10.times do
      d = []
      100000.times { |i| d << i }
    end
  }
end
