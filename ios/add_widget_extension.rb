require 'xcodeproj'

project_path = File.expand_path('Runner.xcodeproj', __dir__)
project = Xcodeproj::Project.open(project_path)

target_name = 'TaxiRadarWidget'
bundle_id = 'com.example.taxiradar.taxiRadarApp.TaxiRadarWidget'
runner_target = project.targets.find { |t| t.name == 'Runner' }

raise "Runner target not found in #{project_path}" unless runner_target

existing_target = project.targets.find { |t| t.name == target_name }
if existing_target
  puts "Target #{target_name} already exists in project."
  exit 0
end

puts "==> Adding #{target_name} extension target to #{project_path}..."

# 1. Create native target for the App Extension
extension_target = project.new_target(:app_extension, target_name, :ios, '16.1')

# 2. Add files group and source files
widget_group = project.main_group.find_subpath(target_name, true)
widget_group.set_source_tree('<group>')
widget_group.set_path(target_name)

files_to_compile = [
  'TaxiRadarWidgetBundle.swift',
  'TaxiRadarWidgetLiveActivity.swift'
]

files_to_compile.each do |filename|
  file_ref = widget_group.new_file(filename)
  extension_target.source_build_phase.add_file_reference(file_ref)
end

# Add entitlements & info.plist to group
widget_group.new_file('Info.plist')
widget_group.new_file('TaxiRadarWidget.entitlements')

# 3. Add system frameworks
%w[WidgetKit SwiftUI ActivityKit].each do |framework|
  ref = project.frameworks_group.new_file("System/Library/Frameworks/#{framework}.framework")
  ref.source_tree = 'SDKROOT'
  extension_target.frameworks_build_phase.add_file_reference(ref)
end

# 4. Configure build settings for all configurations (Debug, Release, Profile)
extension_target.build_configurations.each do |config|
  config.build_settings['PRODUCT_NAME'] = target_name
  config.build_settings['PRODUCT_BUNDLE_IDENTIFIER'] = bundle_id
  config.build_settings['INFOPLIST_FILE'] = "#{target_name}/Info.plist"
  config.build_settings['CODE_SIGN_ENTITLEMENTS'] = "#{target_name}/TaxiRadarWidget.entitlements"
  config.build_settings['SWIFT_VERSION'] = '5.0'
  config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '16.1'
  config.build_settings['TARGETED_DEVICE_FAMILY'] = '1,2'
  config.build_settings['GENERATE_INFOPLIST_FILE'] = 'NO'
  config.build_settings['SKIP_INSTALL'] = 'YES'
  config.build_settings['CURRENT_PROJECT_VERSION'] = '$(FLUTTER_BUILD_NUMBER)'
  config.build_settings['MARKETING_VERSION'] = '$(FLUTTER_BUILD_NAME)'
  config.build_settings['ENABLE_BITCODE'] = 'NO'
  config.build_settings['LD_RUNPATH_SEARCH_PATHS'] = [
    '$(inherited)',
    '@executable_path/Frameworks',
    '@executable_path/../../Frameworks'
  ]
end

# 5. Configure Runner target
runner_target.build_configurations.each do |config|
  config.build_settings['CODE_SIGN_ENTITLEMENTS'] = 'Runner/Runner.entitlements'
end

# Add dependency so building Runner builds extension
runner_target.add_dependency(extension_target)

# Add Embed Foundation Extensions build phase to copy .appex into PlugIns/
embed_phase = runner_target.copy_files_build_phases.find do |phase|
  phase.name == 'Embed Foundation Extensions' || phase.dst_subfolder_spec == 13
end

unless embed_phase
  embed_phase = runner_target.new_copy_files_build_phase('Embed Foundation Extensions')
  embed_phase.dst_subfolder_spec = 13 # 13 corresponds to PlugIns
end

build_file = embed_phase.add_file_reference(extension_target.product_reference)
build_file.settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }

project.save
puts "==> Successfully configured #{target_name} and saved Xcode project!"
