- [Pre-built binaries](#prebuilt)
- [Building](#building)
- [Testing](#testing)
  - [Usage](#testing-usage)
  - [Examples](#testing-examples)


This application is a reference implementation for developers to show how to use the C# API and could
be used to easily check the accuracy. The C# API is a wrapper around the C++ API defined at [https://www.doubango.org/SDKs/mrz/docs/cpp-api.html](https://www.doubango.org/SDKs/mrz/docs/cpp-api.html). A C++ twin sample application is at [c++/recognizer](../../c++/recognizer).

The application accepts path to a JPEG/PNG/BMP file as input. This **is not the recommended** way to use the API. We recommend reading the data directly from the camera and feeding the SDK with the uncompressed **YUV data** without saving it to a file or converting it to RGB.

If you don't want to build this sample and is looking for a quick way to check the accuracy then, try
our cloud-based solution at [https://www.doubango.org/webapps/micr/](https://www.doubango.org/webapps/micr/).

This sample is open source and doesn't require registration or license key.

<a name="prebuilt"></a>
# Pre-built binaries #

If you don't want to build this sample by yourself then, use the pre-built C++ versions:
 - Windows: [recognizer.exe](../../../binaries/windows/x86_64/recognizer.exe) under [binaries/windows/x86_64](../../../binaries/windows/x86_64)
 - Linux: [recognizer](../../../binaries/linux/x86_64/recognizer) under [binaries/linux/x86_64](../../../binaries/linux/x86_64). Built on Ubuntu 18. **You'll need to download libtensorflow.so as explained [here](../../c++/README.md#gpu-acceleration-tensorflow-linux)**.
 - Raspberry Pi: [recognizer](../../../binaries/raspbian/armv7l/recognizer) under [binaries/raspbian/armv7l](../../../binaries/raspbian/armv7l)
 - Android: check [android](../../android) folder
 
On **Windows**, the easiest way to try this sample is to navigate to [binaries/windows/x86_64](../../../binaries/windows/x86_64/) and run [binaries/windows/x86_64/recognizer_cmc7.bat](../../../binaries/windows/x86_64/recognizer_cmc7.bat) or [binaries/windows/x86_64/recognizer_e13b.bat](../../../binaries/windows/x86_64/recognizer_e13b.bat). You can edit these files to use your own images and configuration options.

<a name="building"></a>
# Building #

This sample contains [a single C# source file](Program.cs).

You'll need Visual Studio to build the code. The VS project is at [recognizer.vcxproj](recognizer.vcxproj). Open it.
 - You will need to change the **"Command line arguments"** like the [below image](../../../VC#_config.jpg). Default value: `--assets "$(ProjectDir)..\..\..\assets" --image "$(ProjectDir)..\..\..\assets\images\e13b_1280x720.jpg" --format "e13b+cmc7"`
 
![VC# config](../../../VCsharp_config.jpg)
 
You're now ready to build and run the sample.

<a name="testing-usage"></a>
## Usage ##

`recognizer` is a command line application with the following usage:
```
recognizer \
      --image <path-to-image-with-mrzdata-to-process> \
      [--assets <path-to-assets-folder>] \
      [--format <format-for-dtection:e13b/cmc7/e13b+cmc7>] \
      [--tokenfile <path-to-license-token-file>] \
      [--tokendata <base64-license-token-data>]
```
Options surrounded with **[]** are optional.
- `--image` Path to the image(JPEG/PNG/BMP) to process. You can use default image at [../../../assets/images/e13b_1280x720.jpg](../../../assets/images/e13b_1280x720.jpg).
- `--assets` Path to the [assets](../../../assets) folder containing the configuration files and models. Default value is the current folder.
- `--format` Defines the MICR format to enable for the detection. Use `e13b` to look for E-13B lines only and `cmc7` for CMC-7 lines only. To look for both, use `e13b+cmc7`. For performance reasons you should not use `e13b+cmc7` unless you really expect the document to contain both E-13B and CMC7 lines. Default: `e13b+cmc7`.
- `--tokenfile` Path to the file containing the base64 license token if you have one. If not provided then, the application will act like a trial version. Default: *null*.
- `--tokendata` Base64 license token if you have one. If not provided then, the application will act like a trial version. Default: *null*.

<a name="testing-examples"></a>
## Examples ##
You'll need to change the Visual Studio properties to define the command line arguments.

```
recognizer.exe \
    --image "$(ProjectDir)..\..\..\assets\images\e13b_1280x720.jpg" \
    --assets "$(ProjectDir)..\..\..\assets" \
    --format "e13b+cmc7"
```


