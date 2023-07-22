Protobuf Decoder Tab for Burp Suite
============================

###### Adds a new tab to Burp's HTTP message editor, in order to handle a data serialization for protobuf messages encoded in base64

 ---

This extension provides a new tab on the message editor for requests that contain a specified parameter.

The extension uses the following techniques:
- It creates a custom request tab on the message editor, provided that the `request_binary` or `data_binary` parameter is present
- The value of the `data` parameter is deserialized (URL decoded, then Base64 decoded) and displayed in the custom tab
- Once decoded the Protobuf message will be sent to `protoscope` for deserialization to a human read/editable format
- If the value of the data is modified, the content will be re-serialized (Protoscope serialization to Base64 encoded then URL encoded) and updated in the HttpRequest
- Requests can be live edited from this window

 ---

###### Dependencies

 ---

[Protoscope](https://github.com/protocolbuffers/protoscope) is require for this plugin to function. It can be install by running the following command:

```
go install github.com/protocolbuffers/protoscope/cmd/protoscope...@latest
```

Ensure that it has been installed in the default Go path, `~/go/bin`.

 ---

###### WARNING - RCE - WARNING

 ---

This current iteration of this plugin utilizes a CLI tool to deserialize and reserialize the Protobuf messages in order to allow for updating the values insides the wireframe. As such it sends text from the `data_binary` and `request_binary` to a piped command string. This is fed to ProcessBuilder in order to execute and update our edited Protobuf message. Be aware of what text payloads that get edited as if there are malicious strings they might be ran by the ProcessBuilder.