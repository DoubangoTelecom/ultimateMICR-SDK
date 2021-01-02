'''
    * Copyright (C) 2011-2020 Doubango Telecom <https://www.doubango.org>
    * File author: Mamadou DIOP (Doubango Telecom, France).
    * License: For non commercial use only.
    * Source code: https://github.com/DoubangoTelecom/ultimateMICR-SDK
    * WebSite: https://www.doubango.org/webapps/micr/


    https://github.com/DoubangoTelecom/ultimateMICR/blob/master/SDK_dist/samples/c++/recognizer/README.md
	Usage: 
		recognizer.py \
			--image <path-to-image-with-check-to-recognize> \
			[--assets <path-to-assets-folder>] \
            [--format <format-for-dtection:e13b/cmc7/e13b+cmc7>] \
            [--backprop <whether-to-enable-backpropagation:true/false>] \
            [--ielcd <whether-to-enable-IELCD:true/false>] \
			[--tokenfile <path-to-license-token-file>] \
			[--tokendata <base64-license-token-data>]
	Example:
		recognizer.py \
			--image C:/Projects/GitHub/ultimate/ultimateMICR/SDK_dist/assets/images/e13b_1280x720.jpg \
            --format e13b+cmc7 \
			--assets C:/Projects/GitHub/ultimate/ultimateMICR/SDK_dist/assets \
			--tokenfile C:/Projects/GitHub/ultimate/ultimateMICR/SDK_dev/tokens/windows-iMac.lic
'''

import ultimateMicrSdk
import sys
import argparse
import json
import platform
import os.path
from PIL import Image, ExifTags

# EXIF orientation TAG
ORIENTATION_TAG = [orient for orient in ExifTags.TAGS.keys() if ExifTags.TAGS[orient] == 'Orientation']

# Defines the default JSON configuration. More information at https://www.doubango.org/SDKs/micr/docs/Configuration_options.html
JSON_CONFIG = {
    "debug_level": "info",
    "debug_write_input_image_enabled": False,
    "debug_internal_data_path": ".",
    
    "num_threads": -1,
    "gpgpu_enabled": True,
   
    "backprop": True,
    "interpolation": "bilinear",
    "roi": [0, 0, 0, 0],
    "min_score": 0.4,
    "score_type": "min"
}

TAG = "[PythonRecognizer] "

# Check result
def checkResult(operation, result):
    if not result.isOK():
        print(TAG + operation + ": failed -> " + result.phrase())
        assert False
    else:
        print(TAG + operation + ": OK -> " + result.json())

# Entry point
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="""
    This is the recognizer sample using python language
    """)

    parser.add_argument("--image", required=True, help="Path to the image with MICR data to recognize")
    parser.add_argument("--assets", required=False, default="../../../assets", help="Path to the assets folder")
    parser.add_argument("--format", required=False, default="latin", help="Defines the MICR format to enable for the detection. Use e13b to look for E-13B lines only and cmc7 for CMC-7 lines only")
    parser.add_argument("--backprop", required=False, default=platform.processor()=='i386', help="Whether to enable backpropagation to detect the MICR lines. Only CMC-7 font uses this option. More information at https://www.doubango.org/SDKs/micr/docs/Detection_techniques.html#backpropagation. Default: true for x86 CPUs and false for ARM CPUs.")
    parser.add_argument("--ielcd", required=False, default=platform.processor()=='i386', help="Whether to enable Image Enhancement for Low Contrast Document (IELCD). More information at https://www.doubango.org/SDKs/micr/docs/IELCD.html#ielcd. Default: true for x86 CPUs and false for ARM CPUs.")    
    parser.add_argument("--tokenfile", required=False, default="", help="Path to license token file")
    parser.add_argument("--tokendata", required=False, default="", help="Base64 license token data")

    args = parser.parse_args()
    IMAGE = args.image
    ASSETS = args.assets
    FORMAT = args.format
    TOKEN_FILE = args.tokenfile
    TOKEN_DATA = args.tokendata

    # Check if image exist
    if not os.path.isfile(IMAGE):
        print(TAG + "File doesn't exist: %s" % IMAGE)
        assert False

    # Decode the image
    image = Image.open(IMAGE)
    width, height = image.size
    if image.mode == "RGB":
        format = ultimateMicrSdk.ULTMICR_SDK_IMAGE_TYPE_RGB24
    elif image.mode == "RGBA":
        format = ultimateMicrSdk.ULTMICR_SDK_IMAGE_TYPE_RGBA32
    elif image.mode == "L":
        format = ultimateMicrSdk.ULTMICR_SDK_IMAGE_TYPE_Y
    else:
        print(TAG + "Invalid mode: %s" % image.mode)
        assert False

    # Read the EXIF orientation value
    exif = image._getexif()
    exifOrientation = exif[ORIENTATION_TAG[0]] if len(ORIENTATION_TAG) == 1 and exif != None else 1

    # Update JSON options using values from the command args
    if ASSETS:
        JSON_CONFIG["assets_folder"] = ASSETS
    if FORMAT:
        JSON_CONFIG["format"] = FORMAT
    if TOKEN_FILE:
        JSON_CONFIG["license_token_file"] = TOKEN_FILE
    if TOKEN_DATA:
        JSON_CONFIG["license_token_data"] = TOKEN_DATA

    JSON_CONFIG["backprop"] = (args.backprop == "True")
    JSON_CONFIG["ielcd"] = (args.ielcd == "True")

    # Initialize the engine
    checkResult("Init", 
                ultimateMicrSdk.UltMicrSdkEngine_init(json.dumps(JSON_CONFIG))
               )

    # Recognize/Process
    # Please note that the first time you call this function all deep learning models will be loaded 
    # and initialized which means it will be slow. In your application you've to initialize the engine
    # once and do all the recognitions you need then, deinitialize it.
    checkResult("Process",
                ultimateMicrSdk.UltMicrSdkEngine_process(
                    format,
                    image.tobytes(), # type(x) == bytes
                    width,
                    height,
                    0, # stride
                    exifOrientation
                    )
        )

    # Press any key to exit
    input("\nPress Enter to exit...\n") 

    # DeInit the engine
    checkResult("DeInit", 
                ultimateMicrSdk.UltMicrSdkEngine_deInit()
               )
    
    