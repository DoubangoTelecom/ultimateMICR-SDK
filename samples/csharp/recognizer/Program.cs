/* Copyright (C) 2011-2020 Doubango Telecom <https://www.doubango.org>
* File author: Mamadou DIOP (Doubango Telecom, France).
* License: For non commercial use only.
* Source code: https://github.com/DoubangoTelecom/ultimateMICR-SDK
* WebSite: https://www.doubango.org/webapps/micr/
*/
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.Web.Script.Serialization;
// Include ultimateMICR namespace
using org.doubango.ultimateMicr.Sdk;

/*
	https://github.com/DoubangoTelecom/ultimateMICR-SDK/blob/master/samples/c%2B%2B/recognizer/README.md
	Usage: 
		recognizer \
			---image <path-to-image-with-to-recognize> \
			[--assets <path-to-assets-folder>] \
			[--format <format-for-dtection:e13b/cmc7/e13b+cmc7>] \
			[--tokenfile <path-to-license-token-file>] \
			[--tokendata <base64-license-token-data>]
	Example:
		recognizer \
			--image ultimateMICR-SDK/assets/images/e13b_1280x720.jpg \
			--format e13b+cmc7 \
			--assets ultimateMICR-SDK/assets \
			--tokenfile ultimateMICR-SDK/tokens/windows-iMac.lic
*/

namespace recognizer
{
    class Program
    {
        /**
         * Defines the debug level to output on the console. You should use "verbose" for diagnostic, "info" in development stage and "warn" on production.
         * JSON name: "debug_level"
         * Default: "info"
         * type: string
         * pattern: "verbose" | "info" | "warn" | "error" | "fatal"
         * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#debug-level
         */
        const String CONFIG_DEBUG_LEVEL = "info";

        /**
         * Whether to write the transformed input image to the disk. This could be useful for debugging.
         * JSON name: "debug_write_input_image_enabled"
         * Default: false
         * type: bool
         * pattern: true | false
         * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#debug-write-input-image-enabled
         */
        const bool CONFIG_DEBUG_WRITE_INPUT_IMAGE = false; // must be false unless you're debugging the code

        /**
        * Path to the folder where to write the transformed input image. Used only if "debug_write_input_image_enabled" is true.
        * JSON name: "debug_internal_data_path"
        * Default: ""
        * type: string
        * pattern: folder path
        * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#debug-internal-data-path
        */
        const String CONFIG_DEBUG_DEBUG_INTERNAL_DATA_PATH = ".";

        /**
         * Defines the maximum number of threads to use.
         * You should not change this value unless you know what you’re doing. Set to -1 to let the SDK choose the right value.
         * The right value the SDK will choose will likely be equal to the number of virtual cores.
         * For example, on an octa-core device the maximum number of threads will be 8.
         * JSON name: "num_threads"
         * Default: -1
         * type: int
         * pattern: [-inf, +inf]
         * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#num-threads
         */
        const int CONFIG_NUM_THREADS = -1;

        /**
         * Whether to enable GPGPU computing. This will enable or disable GPGPU computing on the computer vision and deep learning libraries.
         * On ARM devices this flag will be ignored when fixed-point (integer) math implementation exist for a well-defined function.
         * For example, this function will be disabled for the bilinear scaling as we have a fixed-point SIMD accelerated implementation.
         * Same for many deep learning parts as we’re using QINT8 quantized inference.
         * JSON name: "gpgpu_enabled"
         * Default: true
         * type: bool
         * pattern: true | false
         * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#gpgpu-enabled
         */
        const bool CONFIG_GPGPU_ENABLED = true;

        /**
         * A device contains a CPU and a GPU. Both can be used for math operations.
         * This option allows using both units. On some devices the CPU is faster and on other it's slower.
         * When the application starts, the work (math operations to perform) is equally divided: 50% for the CPU and 50% for the GPU.
         * Our code contains a profiler to determine which unit is faster and how fast (percentage) it is. The profiler will change how
         * the work is divided based on the time each unit takes to complete. This is why this configuration entry is named "workload balancing".
         * JSON name: "gpgpu_workload_balancing_enabled"
         * Default: false for x86 and true for ARM
         * type: bool
         * pattern: true | false
         * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#gpgpu-workload-balancing-enabled
         */
        const bool CONFIG_GPGPU_WORKLOAD_BALANCING_ENABLED = false;

        /**
         * Before calling the classifier to determine whether a zone contains a MICR line we need to segment the text using multi-layer segmenter followed by clustering.
         * The multi-layer segmenter uses hysteresis for the voting process using a [min, max] double thresholding values. This configuration entry defines how low the
         * thresholding values should be. Lower the values are, higher the number of fragments will be and higher the recall will be. High number of fragments means more
         * data to process which means more CPU usage and higher processing time.
         * JSON name: "segmenter_accuracy"
         * Default: high
         * type: string
         * pattern: "veryhigh" | "high" | "medium" | "low" | "verylow"
         * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#segmenter-accuracy
         */
        const String CONFIG_SEGMENTER_ACCURACY = "high";

        /**
         * Whether to enable backpropagation to detect the MICR lines. Only CMC-7 font uses this option.
         * Technical description at https://www.doubango.org/SDKs/micr/docs/Detection_techniques.html#backpropagation.
         * JSON name: "backpropagation_enabled"
         * Default: true for x86 CPUs and false for ARM CPUs.
         * type: bool
         * pattern: true | false
         * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#segmenter-accuracy
         */
        const bool CONFIG_BACKPROPAGATION_ENABLED = true;

        /**
         * Defines the interpolation method to use when pixels are scaled, deskewed or deslanted. bicubic offers the best quality but is slow as there
         * is no SIMD or GPU acceleration yet. bilinear and nearest interpolations are multithreaded and SIMD accelerated. For most scenarios bilinear
         * interpolation is good enough to provide high accuracy/precision results while the code still runs very fast.
         * JSON name: "interpolation"
         * Default: bilinear
         * type: string
         * pattern: "nearest" | "bilinear" | "bicubic"
         * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#interpolation
         */
        const String CONFIG_INTERPOLATION = "bilinear";

        /**
         * Defines the MICR format to enable for the detection. Use "e13b" to look for E-13B lines only and "cmc7" for CMC-7 lines only. To look for both, use "e13b+cmc7".
         * For performance reasons you should not use  "e13b+cmc7" unless you really expect the document to contain both E-13B and CMC7 lines.
         * JSON name: "interpolation"
         * Default: "e13b+cmc7"
         * type: string
         * pattern: "e13b" | "cmc7" | "e13b+cmc7"
         * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#format
         */
        const String CONFIG_FORMAT = "e13b+cmc7";

        /**
         * Define a threshold for the overall recognition score. Any recognition with a score below that threshold will be ignored.
         * The overall score is computed based on "score_type". 0.f being poor confidence and 1.f excellent confidence.
         * JSON name: "min_score"
         * Default: 0.3f
         * type: float
         * pattern: ]0.f, 1.f]
         * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#min-score
         */
        const double CONFIG_MIN_SCORE = 0.4; // 40%

        /**
         * Defines the overall score type. The recognizer outputs a recognition score ([0.f, 1.f]) for every character in the license plate.
         * The score type defines how to compute the overall score.
         * - "min": Takes the minimum score.
         * - "mean": Takes the average score.
         * - "median": Takes the median score.
         * - "max": Takes the maximum score.
         * - "minmax": Takes (max + min) * 0.5f.
         * The "min" score is the more robust type as it ensure that every character have at least a certain confidence value.
         * The median score is the default type as it provide a higher recall. In production we recommend using min type.
         * JSON name: "recogn_score_type"
         * Default: "median"
         * Recommended: "min"
         * type: string
         *  More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#recogn-score-type
         */
        const String CONFIG_SCORE_TYPE = "min";

        /**
         * Defines the Region Of Interest (ROI) for the detector. Any pixels outside region of interest will be ignored by the detector.
         * Defining an WxH region of interest instead of resizing the image at WxH is very important as you'll keep the same quality when you define a ROI while you'll lose in quality when using the later.
         * JSON name: "roi"
         * Default: [0.f, 0.f, 0.f, 0.f]
         * type: float[4]
         * pattern: [left, right, top, bottom]
         * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#roi
         */
        static readonly IList<float> CONFIG_ROI = new[] { 0f, 0f, 0f, 0f };

        static void Main(String[] args)
        {
            // Parse arguments
            IDictionary<String, String> parameters = ParseArgs(args);

            // Make sur the image is provided using args
            if (!parameters.ContainsKey("--image"))
            {
                Console.Error.WriteLine("--image required");
                throw new Exception("--image required");
            }
            // Extract assets folder
            // https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#assets-folder
            String assetsFolder = parameters.ContainsKey("--assets")
                ? parameters["--assets"] : String.Empty;

            // License data - Optional
            // https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#license-token-data
            String tokenDataBase64 = parameters.ContainsKey("--tokendata")
                ? parameters["--tokendata"] : String.Empty;

            // Format - Optional
            // https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#format
            String format = parameters.ContainsKey("--format")
                ? parameters["--format"] : CONFIG_FORMAT;

            // Initialize the engine: Load deep learning models and init GPU shaders
            // Make sure de disable VS hosting process to see logs from native code: https://social.msdn.microsoft.com/Forums/en-US/5da6cdb2-bc2b-4fff-8adf-752b32143dae/printf-from-dll-in-console-app-in-visual-studio-c-2010-express-does-not-output-to-console-window?forum=Vsexpressvcs
            // This function should be called once.
            // https://www.doubango.org/SDKs/micr/docs/cpp-api.html#_CPPv4N15ultimateMicrSdk16UltMicrSdkEngine4initEPKc
            UltMicrSdkResult result = CheckResult("Init", UltMicrSdkEngine.init(BuildJSON(format, assetsFolder, tokenDataBase64)));

            // Decode the JPEG/PNG/BMP file
            String file = parameters["--image"];
            if (!System.IO.File.Exists(file))
            {
                throw new System.IO.FileNotFoundException("File not found:" + file);
            }
            Bitmap image = new Bitmap(file);
            if (Image.GetPixelFormatSize(image.PixelFormat) == 24 && ((image.Width * 3) & 3) != 0)
            {
                //!\\ Not DWORD aligned -> the stride will be multiple of 4-bytes instead of 3-bytes
                // ultimateMICR requires stride to be in samples unit instead of in bytes
                Console.Error.WriteLine(String.Format("//!\\ The image width ({0}) not a multiple of DWORD.", image.Width));
                image = new Bitmap(image, new Size((image.Width + 3) & -4, image.Height));
            }
            int bytesPerPixel = Image.GetPixelFormatSize(image.PixelFormat) >> 3;
            if (bytesPerPixel != 1 && bytesPerPixel != 3 && bytesPerPixel != 4)
            {
                throw new System.Exception("Invalid BPP:" + bytesPerPixel);
            }

            // Extract Exif orientation
            const int ExifOrientationTagId = 0x112;
            int orientation = 1;
            if (Array.IndexOf(image.PropertyIdList, ExifOrientationTagId) > -1)
            {
                int orientation_ = image.GetPropertyItem(ExifOrientationTagId).Value[0];
                if (orientation_ >= 1 && orientation_ <= 8)
                {
                    orientation = orientation_;
                }
            }

            // Processing: Detection + recognition
            //!\\ First inference is expected to be slow (deep learning models mapping to CPU/GPU memory)
            BitmapData imageData = image.LockBits(new Rectangle(0, 0, image.Width, image.Height), ImageLockMode.ReadOnly, image.PixelFormat);
            try
            {
                // For packed formats (RGB-family): https://www.doubango.org/SDKs/micr/docs/cpp-api.html#_CPPv4N15ultimateMicrSdk16UltMicrSdkEngine7processEK22ULTMICR_SDK_IMAGE_TYPEPKvK6size_tK6size_tK6size_tKi
                // For YUV formats (data from camera): https://www.doubango.org/SDKs/micr/docs/cpp-api.html#_CPPv4N15ultimateMicrSdk16UltMicrSdkEngine7processEK22ULTMICR_SDK_IMAGE_TYPEPKvPKvPKvK6size_tK6size_tK6size_tK6size_tK6size_tK6size_tKi
                result = CheckResult("Process", UltMicrSdkEngine.process(
                        (bytesPerPixel == 1) ? ULTMICR_SDK_IMAGE_TYPE.ULTMICR_SDK_IMAGE_TYPE_Y : (bytesPerPixel == 4 ? ULTMICR_SDK_IMAGE_TYPE.ULTMICR_SDK_IMAGE_TYPE_RGBA32 : ULTMICR_SDK_IMAGE_TYPE.ULTMICR_SDK_IMAGE_TYPE_RGB24), // TODO(dmi): not correct. C# image decoder outputs BGR24 instead of RGB24
                        imageData.Scan0,
                        (uint)imageData.Width,
                        (uint)imageData.Height,
                        (uint)(imageData.Stride / bytesPerPixel),
                        orientation
                    )); 
                // Print result to console
                Console.WriteLine("Result: {0}", result.json());
            }
            finally
            {
                image.UnlockBits(imageData);
            }

            // Write until user press a key
            Console.WriteLine("Press any key to terminate !!");
            Console.Read();

            // Now that you're done, deInit the engine before exiting
            CheckResult("DeInit", UltMicrSdkEngine.deInit());
        }

        static IDictionary<String, String> ParseArgs(String[] args)
        {
            Console.WriteLine("Args: {0}", string.Join(" ", args));

            if ((args.Length & 1) != 0)
            {
                String errMessage = String.Format("Number of args must be even: {0}", args.Length);
                Console.Error.WriteLine(errMessage);
                throw new Exception(errMessage);
            }

            // Parsing
            Dictionary<String, String> values = new Dictionary<String, String>();
            for (int index = 0; index < args.Length; index += 2)
            {
                String key = args[index];
                if (key.Length < 2 || key[0] != '-' || key[1] != '-')
                {
                    String errMessage = String.Format("Invalid key: {0}", key);
                    Console.Error.WriteLine(errMessage);
                    throw new Exception(errMessage);
                }
                values[key] = args[index + 1].Replace("$(ProjectDir)", Properties.Resources.RecognizerProjectDir.Trim()); // Patch path to use project directory
            }
            return values;
        }

        static UltMicrSdkResult CheckResult(String functionName, UltMicrSdkResult result)
        {
            if (!result.isOK())
            {
                String errMessage = String.Format("{0}: Execution failed: {1}", new String[] { functionName, result.json() });
                Console.Error.WriteLine(errMessage);
                throw new Exception(errMessage);
            }
            return result;
        }

        // https://www.doubango.org/SDKs/micr/docs/Configuration_options.html
        static String BuildJSON(String format, String assetsFolder = "", String tokenDataBase64 = "")
        {
            return new JavaScriptSerializer().Serialize(new
            {
                debug_level = CONFIG_DEBUG_LEVEL,
                debug_write_input_image_enabled = CONFIG_DEBUG_WRITE_INPUT_IMAGE,
                debug_internal_data_path = CONFIG_DEBUG_DEBUG_INTERNAL_DATA_PATH,

                num_threads = CONFIG_NUM_THREADS,
                gpgpu_enabled = CONFIG_GPGPU_ENABLED,
                gpgpu_workload_balancing_enabled = CONFIG_GPGPU_WORKLOAD_BALANCING_ENABLED,

                segmenter_accuracy = CONFIG_SEGMENTER_ACCURACY,
                backpropagation_enabled = CONFIG_BACKPROPAGATION_ENABLED,
                interpolation = CONFIG_INTERPOLATION,

                roi = CONFIG_ROI,
                score_type = CONFIG_SCORE_TYPE,
                min_score = CONFIG_MIN_SCORE,

                // Value added using command line args
                format = format,
                assets_folder = assetsFolder,
                license_token_data = tokenDataBase64,
            });
        }        
    }
}
