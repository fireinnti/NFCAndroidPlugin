extends Node2D

var _android_plugin: Object
var _status: Label

func _ready() -> void:
	_status = Label.new()
	_status.position = Vector2(30, 30)
	add_child(_status)
	if Engine.has_singleton("GodotAndroidNFCPlugin"):
		_android_plugin = Engine.get_singleton("GodotAndroidNFCPlugin")
		_android_plugin.connect("tag_read", _on_tag_read)
		_android_plugin.connect("nfc_error", _on_error)
		_android_plugin.call("enableNFC")
		_status.text = "Scan a text or URI tag."
	else:
		_status.text = "Run this demo on Android to use NFC."

func _on_tag_read(value: String) -> void:
	_status.text = "Received: " + value

func _on_error(message: String) -> void:
	_status.text = message

func _on_Button_pressed() -> void:
	if _android_plugin:
		_status.text = "NFC available: %s; enabled: %s" % [_android_plugin.call("isNFCAvailable"), _android_plugin.call("isNFCEnabled")]
