# -*- encoding: utf-8 -*-

# Update these to get proper version and commit history

Gem::Specification.new do |s|
  s.name = %q{persistent_data_structure}
  s.version = "0.01"
  s.authors = ["Smit Shah"]
  s.date = Time.now.strftime('%Y-%m-%d')
  s.summary = 'Persistent data structures'
  s.description = s.summary
  s.email = ["who828@gmail.com"]
  s.require_paths = ["lib"]
  s.licenses = ["Apache-2.0"]
  s.test_files = Dir["test/test*.rb"]
  if defined?(JRUBY_VERSION)
    s.files = Dir['lib/persistent_data_structure.jar']
    s.platform = 'java'
  else
    s.extensions = 'ext/extconf.rb'
  end
  s.files += `git ls-files`.lines.map(&:chomp)
end
