/* Copyright (C) 2011-2020 Doubango Telecom <https://www.doubango.org>
* File author: Mamadou DIOP (Doubango Telecom, France).
* License: For non commercial use only.
* Source code: https://github.com/DoubangoTelecom/ultimateMICR-SDK
* WebSite: https://www.doubango.org/webapps/micr/
*/

import java.io.File;
import java.lang.IllegalArgumentException;
import java.nio.ByteBuffer;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Hashtable;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import javax.imageio.ImageIO;

import org.doubango.ultimateMicr.Sdk.ULTMICR_SDK_IMAGE_TYPE;
import org.doubango.ultimateMicr.Sdk.UltMicrSdkEngine;
import org.doubango.ultimateMicr.Sdk.UltMicrSdkResult;

public class Recognizer {

   /**
   * Defines the debug level to output on the console. You should use "verbose" for diagnostic, "info" in development stage and "warn" on production.
   * JSON name: "debug_level"
   * Default: "info"
   * type: string
   * pattern: "verbose" | "info" | "warn" | "error" | "fatal"
   * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#debug-level
   */
  static final String CONFIG_DEBUG_LEVEL = "info";

   /**
   * Whether to write the transformed input image to the disk. This could be useful for debugging.
   * JSON name: "debug_write_input_image_enabled"
   * Default: false
   * type: bool
   * pattern: true | false
   * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#debug-write-input-image-enabled
   */
  static final boolean CONFIG_DEBUG_WRITE_INPUT_IMAGE = false; // must be false unless you're debugging the code

   /**
    * Path to the folder where to write the transformed input image. Used only if "debug_write_input_image_enabled" is true.
   * JSON name: "debug_internal_data_path"
   * Default: ""
   * type: string
   * pattern: folder path
   * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#debug-internal-data-path
   */
  static final String CONFIG_DEBUG_DEBUG_INTERNAL_DATA_PATH = ".";

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
  static final int CONFIG_NUM_THREADS = -1;

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
  static final boolean CONFIG_GPGPU_ENABLED = true;

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
  static final boolean CONFIG_GPGPU_WORKLOAD_BALANCING_ENABLED = false;

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
  static final String CONFIG_SEGMENTER_ACCURACY = "high";

   /**
   * Whether to enable backpropagation to detect the MICR lines. Only CMC-7 font uses this option.
   * Technical description at https://www.doubango.org/SDKs/micr/docs/Detection_techniques.html#backpropagation.
   * JSON name: "backpropagation_enabled"
   * Default: true for x86 CPUs and false for ARM CPUs.
   * type: bool
   * pattern: true | false
   * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#segmenter-accuracy
   */
  static final boolean CONFIG_BACKPROPAGATION_ENABLED = true;

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
  static final String CONFIG_INTERPOLATION = "bilinear";

   /**
   * Defines the MICR format to enable for the detection. Use "e13b" to look for E-13B lines only and "cmc7" for CMC-7 lines only. To look for both, use "e13b+cmc7".
   * For performance reasons you should not use  "e13b+cmc7" unless you really expect the document to contain both E-13B and CMC7 lines.
   * JSON name: "interpolation"
   * Default: "e13b+cmc7"
   * type: string
   * pattern: "e13b" | "cmc7" | "e13b+cmc7"
   * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#format
   */
  static final String CONFIG_FORMAT = "e13b+cmc7";

   /**
   * Define a threshold for the overall recognition score. Any recognition with a score below that threshold will be ignored.
   * The overall score is computed based on "score_type". 0.f being poor confidence and 1.f excellent confidence.
   * JSON name: "min_score"
   * Default: 0.3f
   * type: float
   * pattern: ]0.f, 1.f]
   * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#min-score
   */
  static final double CONFIG_MIN_SCORE = 0.4; // 40%

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
  static final String CONFIG_SCORE_TYPE = "min";

   /**
   * Defines the Region Of Interest (ROI) for the detector. Any pixels outside region of interest will be ignored by the detector.
   * Defining an WxH region of interest instead of resizing the image at WxH is very important as you'll keep the same quality when you define a ROI while you'll lose in quality when using the later.
   * JSON name: "roi"
   * Default: [0.f, 0.f, 0.f, 0.f]
   * type: float[4]
   * pattern: [left, right, top, bottom]
   * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#roi
   */
   static final List<Float> CONFIG_ROI = Arrays.asList(0.f, 0.f, 0.f, 0.f);

   public static void main(String[] args) throws IllegalArgumentException, FileNotFoundException, IOException {
      // Parse arguments
      final Hashtable<String, String> parameters = ParseArgs(args);

      // Make sur the image is provided using args
      if (!parameters.containsKey("--image"))
      {
         System.err.println("--image required");
         throw new IllegalArgumentException("--image required");
      }
      // Extract assets folder
      // https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#assets-folder
      String assetsFolder = parameters.containsKey("--assets")
          ? parameters.get("--assets") : "";

      // License data - Optional
      // https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#license-token-data
      String tokenDataBase64 = parameters.containsKey("--tokendata")
          ? parameters.get("--tokendata") : "";

      // Format - Optional
      // https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#format
      String format = parameters.containsKey("--format")
            ? parameters.get("--format") : CONFIG_FORMAT;

      //!\\ This is a quick and dirty way to load the library. You should not use it:
      // create a static block outside the main function and load the library from there.
      // In the next version we'll make sure the library has the same name regardless the platform/OS.
      System.loadLibrary(System.getProperty("os.name").toLowerCase().contains("win") ? "ultimateMICR-SDK" : "ultimate_micr-sdk");

      // Initialize the engine: Load deep learning models and init GPU shaders
      // Make sure de disable VS hosting process to see logs from native code: https://social.msdn.microsoft.com/Forums/en-US/5da6cdb2-bc2b-4fff-8adf-752b32143dae/printf-from-dll-in-console-app-in-visual-studio-c-2010-express-does-not-output-to-console-window?forum=Vsexpressvcs
      // This function should be called once.
      // https://www.doubango.org/SDKs/micr/docs/cpp-api.html#_CPPv4N15ultimateMicrSdk16UltMicrSdkEngine4initEPKc
      UltMicrSdkResult result = CheckResult("Init", UltMicrSdkEngine.init(BuildJSON(format, assetsFolder, tokenDataBase64)));

      // Decode the JPEG/PNG/BMP file
      final File file = new File(parameters.get("--image"));
      if (!file.exists())
      {
          throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
      }
      final BufferedImage image = ImageIO.read(file);
      final int bytesPerPixel = image.getColorModel().getPixelSize() >> 3;
      if (bytesPerPixel != 1 && bytesPerPixel != 3 && bytesPerPixel != 4)
      {
         throw new IOException("Invalid BPP: " + bytesPerPixel);
      }
      System.out.println("bytesPerPixel: " + bytesPerPixel + System.lineSeparator());

      // Write data to native/direct ByteBuffer
      final DataBuffer dataBuffer = image.getRaster().getDataBuffer();
      if (!(dataBuffer instanceof DataBufferByte)) {
         throw new IOException("Image must contains 1-byte samples");
      }
      final ByteBuffer nativeBuffer = ByteBuffer.allocateDirect(image.getWidth() * image.getHeight() * bytesPerPixel);
      final byte[] pixelData = ((DataBufferByte) dataBuffer).getData();
      nativeBuffer.put(pixelData);
      nativeBuffer.rewind();

      // TODO(dmi): add code to extract EXIF orientation
      final int orientation = 1;
      
      // Processing
      // For packed formats (RGB-family): https://www.doubango.org/SDKs/micr/docs/cpp-api.html#_CPPv4N15ultimateMicrSdk16UltMicrSdkEngine7processEK22ULTMICR_SDK_IMAGE_TYPEPKvK6size_tK6size_tK6size_tKi
      // For YUV formats (data from camera): https://www.doubango.org/SDKs/micr/docs/cpp-api.html#_CPPv4N15ultimateMicrSdk16UltMicrSdkEngine7processEK22ULTMICR_SDK_IMAGE_TYPEPKvPKvPKvK6size_tK6size_tK6size_tK6size_tK6size_tK6size_tKi
      result = CheckResult("Process", UltMicrSdkEngine.process(
            (bytesPerPixel == 1) ? ULTMICR_SDK_IMAGE_TYPE.ULTMICR_SDK_IMAGE_TYPE_Y : (bytesPerPixel == 4 ? ULTMICR_SDK_IMAGE_TYPE.ULTMICR_SDK_IMAGE_TYPE_BGRA32 : ULTMICR_SDK_IMAGE_TYPE.ULTMICR_SDK_IMAGE_TYPE_BGR24),
            nativeBuffer,
            image.getWidth(),
            image.getHeight(),
            image.getWidth(), // stride
            orientation
         ));
      // Print result to console
      System.out.println("Result: " + result.json() + System.lineSeparator());

       // Wait until user press a key
       System.out.println("Press any key to terminate !!" + System.lineSeparator());
       final java.util.Scanner scanner = new java.util.Scanner(System.in);
       if (scanner != null) {
         scanner.nextLine();
         scanner.close();
       }

       // Now that you're done, deInit the engine before exiting
       CheckResult("DeInit", UltMicrSdkEngine.deInit());
   }

   static Hashtable<String, String> ParseArgs(String[] args) throws IllegalArgumentException
   {
      System.out.println("Args: " + String.join(" ", args) + System.lineSeparator());

      if ((args.length & 1) != 0)
      {
            String errMessage = String.format("Number of args must be even: %d", args.length);
            System.err.println(errMessage);
            throw new IllegalArgumentException(errMessage);
      }

      // Parsing
      Hashtable<String, String> values = new Hashtable<String, String>();
      for (int index = 0; index < args.length; index += 2)
      {
            String key = args[index];
            if (!key.startsWith("--"))
            {
               String errMessage = String.format("Invalid key: %s", key);
               System.err.println(errMessage);
               throw new IllegalArgumentException(errMessage);
            }
            values.put(key, args[index + 1].replace("$(ProjectDir)", System.getProperty("user.dir").trim()));
      }
      return values;
   }

   static UltMicrSdkResult CheckResult(String functionName, UltMicrSdkResult result) throws IOException
   {
      if (!result.isOK())
      {
            String errMessage = String.format("%s: Execution failed: %s", functionName, result.json());
            System.err.println(errMessage);
            throw new IOException(errMessage);
      }
      return result;
   }

   // https://www.doubango.org/SDKs/micr/docs/Configuration_options.html
   static String BuildJSON(String format, String assetsFolder, String tokenDataBase64)
   {
      return String.format(
         "{" +
         "\"debug_level\": \"%s\"," +
         "\"debug_write_input_image_enabled\": %s," +
         "\"debug_internal_data_path\": \"%s\"," +
         "" +
         "\"num_threads\": %d," +
         "\"gpgpu_enabled\": %s," +
         "\"gpgpu_workload_balancing_enabled\": %s," +
         "" +
         "\"format\": \"%s\"," +
         "\"interpolation\": \"%s\"," +
         "\"backpropagation_enabled\": %s," +
         "" +
         "\"roi\": [%s]," +
         "\"min_score\": %f," + 
         "\"score_type\": \"%s\"," +
         "" +
         "\"assets_folder\": \"%s\"," +
         "\"license_token_data\": \"%s\"" +
         "" +
         "}"
         , 
         CONFIG_DEBUG_LEVEL,
         CONFIG_DEBUG_WRITE_INPUT_IMAGE ? "true" : "false",
         CONFIG_DEBUG_DEBUG_INTERNAL_DATA_PATH,

         CONFIG_NUM_THREADS,
         CONFIG_GPGPU_ENABLED ? "true" : "false",
         CONFIG_GPGPU_WORKLOAD_BALANCING_ENABLED ? "true" : "false",

         format,
         CONFIG_INTERPOLATION,
         CONFIG_BACKPROPAGATION_ENABLED ? "true" : "false",

         CONFIG_ROI.stream().map(String::valueOf).collect(Collectors.joining(",")),
         CONFIG_MIN_SCORE,
         CONFIG_SCORE_TYPE,

         assetsFolder,
         tokenDataBase64
      );
   }
}