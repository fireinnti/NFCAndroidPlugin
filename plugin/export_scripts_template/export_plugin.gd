@tool
extends EditorPlugin

var export_plugin: AndroidExportPlugin

func _enter_tree() -> void:
	for key in {"nfc/links/custom_scheme": "deadright", "nfc/links/custom_host": "card", "nfc/links/web_host": "", "nfc/links/web_path_prefix": "/nfc/"}:
		if not ProjectSettings.has_setting(key):
			var value = {"nfc/links/custom_scheme": "deadright", "nfc/links/custom_host": "card", "nfc/links/web_host": "", "nfc/links/web_path_prefix": "/nfc/"}[key]
			ProjectSettings.set_setting(key, value)
			ProjectSettings.set_initial_value(key, value)
		ProjectSettings.add_property_info({"name": key, "type": TYPE_STRING})
	export_plugin = AndroidExportPlugin.new()
	add_export_plugin(export_plugin)

func _exit_tree() -> void:
	remove_export_plugin(export_plugin)
	export_plugin = null

class AndroidExportPlugin extends EditorExportPlugin:
	const PLUGIN_NAME = "GodotAndroidNFCPlugin"

	func _supports_platform(platform: EditorExportPlatform) -> bool:
		return platform is EditorExportPlatformAndroid

	func _get_android_libraries(_platform: EditorExportPlatform, debug: bool) -> PackedStringArray:
		var variant := "debug" if debug else "release"
		return PackedStringArray(["res://addons/%s/bin/%s/%s-%s.aar" % [PLUGIN_NAME, variant, PLUGIN_NAME, variant]])

	func _get_android_manifest_application_element_contents(_platform: EditorExportPlatform, _debug: bool) -> String:
		var scheme := str(ProjectSettings.get_setting("nfc/links/custom_scheme", "deadright")).xml_escape(true)
		var host := str(ProjectSettings.get_setting("nfc/links/custom_host", "card")).xml_escape(true)
		var web_host := str(ProjectSettings.get_setting("nfc/links/web_host", "")).strip_edges().xml_escape(true)
		var path := str(ProjectSettings.get_setting("nfc/links/web_path_prefix", "/nfc/")).xml_escape(true)
		var contents := """
<activity android:name="org.godotengine.plugin.android.nfc.GodotAndroidNFCActivityV2"
    android:exported="true" android:excludeFromRecents="true"
    android:theme="@android:style/Theme.Translucent.NoTitleBar">
    <intent-filter>
        <action android:name="android.nfc.action.NDEF_DISCOVERED" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.nfc.action.NDEF_DISCOVERED" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="%s" android:host="%s" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="%s" android:host="%s" />
    </intent-filter>
""" % [scheme, host, scheme, host]
		if not web_host.is_empty():
			# Android 16+ dispatches web NFC tags as VIEW. Older Android uses NDEF.
			contents += """
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="http" />
        <data android:scheme="https" android:host="%s" android:pathPrefix="%s" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.nfc.action.NDEF_DISCOVERED" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="http" />
        <data android:scheme="https" android:host="%s" android:pathPrefix="%s" />
    </intent-filter>
""" % [web_host, path, web_host, path]
		return contents + "</activity>"

	func _get_name() -> String:
		return PLUGIN_NAME
