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
  
  # Ensure TaxiRadarAttributes.swift is in Runner
  runner_group = project.main_group.find_subpath('Runner', false) || project.main_group['Runner']
  if runner_group
    runner_attr_ref = runner_group.files.find { |f| f.path == 'TaxiRadarAttributes.swift' }
    runner_attr_ref ||= runner_group.new_file('TaxiRadarAttributes.swift')
    unless runner_target.source_build_phase.files.any? { |bf| bf.file_ref == runner_attr_ref }
      runner_target.source_build_phase.add_file_reference(runner_attr_ref)
    end
  end

  # Ensure TaxiRadarAttributes.swift is in extension
  widget_group = project.main_group.find_subpath(target_name, false) || project.main_group[target_name]
  if widget_group
    widget_attr_ref = widget_group.files.find { |f| f.path == 'TaxiRadarAttributes.swift' }
    widget_attr_ref ||= widget_group.new_file('TaxiRadarAttributes.swift')
    unless existing_target.source_build_phase.files.any? { |bf| bf.file_ref == widget_attr_ref }
      existing_target.source_build_phase.add_file_reference(widget_attr_ref)
    end
  end

  project.save
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
  'TaxiRadarAttributes.swift',
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
  config.build_settings['CURRENT_PROJECT_VERSION'] = '1'
  config.build_settings['MARKETING_VERSION'] = '1.0.0'
  config.build_settings['ENABLE_BITCODE'] = 'NO'
  config.build_settings['CODE_SIGNING_ALLOWED'] = 'NO'
  config.build_settings['CODE_SIGNING_REQUIRED'] = 'NO'
  config.build_settings['CODE_SIGN_IDENTITY'] = ''
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

# Ensure TaxiRadarAttributes.swift is in Runner
runner_group = project.main_group.find_subpath('Runner', false) || project.main_group['Runner']
if runner_group
  runner_attr_ref = runner_group.files.find { |f| f.path == 'TaxiRadarAttributes.swift' }
  runner_attr_ref ||= runner_group.new_file('TaxiRadarAttributes.swift')
  unless runner_target.source_build_phase.files.any? { |bf| bf.file_ref == runner_attr_ref }
    runner_target.source_build_phase.add_file_reference(runner_attr_ref)
    puts "==> Added TaxiRadarAttributes.swift to Runner compile sources."
  end
end

# Add dependency so building Runner builds extension
runner_target.add_dependency(extension_target)

# Add Embed Foundation Extensions build phase to copy .appex into PlugIns/
embed_phase = runner_target.copy_files_build_phases.find do |phase|
  phase.respond_to?(:name) && (phase.name == 'Embed Foundation Extensions' || phase.dst_subfolder_spec.to_s == '13')
end

unless embed_phase
  embed_phase = runner_target.new_copy_files_build_phase('Embed Foundation Extensions')
  embed_phase.dst_subfolder_spec = '13' # String '13' corresponds to PlugIns in xcodeproj
  embed_phase.dst_path = ''
end

build_file = embed_phase.add_file_reference(extension_target.product_reference)
build_file.settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }

# Reorder build phases: Embed Foundation Extensions MUST be BEFORE Embed Frameworks and Thin Binary
# Otherwise Xcode creates a dependency cycle: Copy PlugIns -> Thin Binary -> Info.plist -> Copy PlugIns
embed_frameworks_idx = runner_target.build_phases.index { |p| p.respond_to?(:name) && p.name == 'Embed Frameworks' }
thin_binary_idx = runner_target.build_phases.index { |p| p.respond_to?(:name) && p.name == 'Thin Binary' }

candidate_indices = [embed_frameworks_idx, thin_binary_idx].compact
if candidate_indices.any?
  insert_target_idx = candidate_indices.min
  runner_target.build_phases.delete(embed_phase)
  runner_target.build_phases.insert(insert_target_idx, embed_phase)
  puts "==> Positioned 'Embed Foundation Extensions' at build phase index #{insert_target_idx}"
end

project.save
puts "==> Successfully configured #{target_name} and saved Xcode project (build phase reordered)!"
