/* Copyright (C) 2011-2021 Doubango Telecom <https://www.doubango.org>
* File author: Mamadou DIOP (Doubango Telecom, France).
* License: For non commercial use only.
* Source code: https://github.com/DoubangoTelecom/ultimateMICR-SDK
* WebSite: https://www.doubango.org/webapps/micr/
*/

/*
	https://github.com/DoubangoTelecom/ultimateMICR/blob/master/SDK_dist/samples/c++/recognizer/README.md
	Usage: 
		recognizer \
			--image <path-to-image-with-micr-zone-to-recognize> \
			[--assets <path-to-assets-folder>] \
			[--format <format-for-dtection:e13b/cmc7/e13b+cmc7>] \
			[--backprop <whether-to-enable-backpropagation:true/false>] \
			[--ielcd <whether-to-enable-IELCD:true/false>] \
			[--tokenfile <path-to-license-token-file>] \
			[--tokendata <base64-license-token-data>]

	Example:
		recognizer \
			--image C:/Projects/GitHub/ultimate/ultimateMICR/SDK_dist/assets/images/e13b_1280x720.jpg \
			--assets C:/Projects/GitHub/ultimate/ultimateMICR/SDK_dist/assets \
			--format e13b+cmc7 \
			--backprop true \
			--tokenfile C:/Projects/GitHub/ultimate/ultimateMICR/SDK_dev/tokens/windows-iMac.lic
		
*/

#include <ultimateMICR-SDK-API-PUBLIC.h>
#include <sys/stat.h>
#include <map>
#if defined(_WIN32)
#include <algorithm> // std::replace
#endif

// Not part of the SDK, used to decode images -> https://github.com/nothings/stb
#define STB_IMAGE_IMPLEMENTATION
#define STB_IMAGE_STATIC
#include "../stb_image.h"

using namespace ultimateMicrSdk;

// Asset manager used on Android to files in "assets" folder
#if ULTMICR_SDK_OS_ANDROID 
#	define ASSET_MGR_PARAM() __sdk_android_assetmgr, 
#else
#	define ASSET_MGR_PARAM() 
#endif /* ULTMICR_SDK_OS_ANDROID */

struct MicrFile {
	int width = 0, height = 0, channels = 0;
	stbi_uc* uncompressedDataPtr = nullptr;
	void* compressedDataPtr = nullptr;
	size_t compressedDataSize = 0;
	FILE* filePtr = nullptr;
	virtual ~MicrFile() {
		if (uncompressedDataPtr) free(uncompressedDataPtr), uncompressedDataPtr = nullptr;
		if (compressedDataPtr) free(compressedDataPtr), compressedDataPtr = nullptr;
		if (filePtr) fclose(filePtr), filePtr = nullptr;
	}
	bool isValid() const {
		return width > 0 && height > 0 && (channels == 1 || channels == 3 || channels == 4) && uncompressedDataPtr && compressedDataPtr && compressedDataSize > 0;
	}
};

static void printUsage(const std::string& message = "");
static bool parseArgs(int argc, char *argv[], std::map<std::string, std::string >& values);
static bool readFile(const std::string& path, MicrFile& file);

// Configuration for ANPR deep learning engine
static const char* __jsonConfig =
"{"
"\"debug_level\": \"info\","
"\"debug_write_input_image_enabled\": false,"
"\"debug_internal_data_path\": \".\","
""
"\"num_threads\": -1,"
"\"gpgpu_enabled\": true,"
""
"\"interpolation\": \"bilinear\","
"\"roi\": [0, 0, 0, 0],"
"\"min_score\": 0.4,"
"\"score_type\": \"min\""
"";

/*
* Entry point
*/
int main(int argc, char *argv[])
{
	// local variables
	UltMicrSdkResult result(0, "OK", "{}");
	std::string assetsFolder, format = "e13b+cmc7", licenseTokenData, licenseTokenFile;
#if defined(__arm__) || defined(__thumb__) || defined(__TARGET_ARCH_ARM) || defined(__TARGET_ARCH_THUMB) || defined(_ARM) || defined(_M_ARM) || defined(_M_ARMT) || defined(__arm) || defined(__aarch64__)
	bool backpropEnabled = false;
	bool ielcdEnabled = false;
#else
	bool backpropEnabled = true;
	bool ielcdEnabled = true;
#endif
	std::string pathFileImage;

	// Parsing args
	std::map<std::string, std::string > args;
	if (!parseArgs(argc, argv, args)) {
		printUsage();
		return -1;
	}
	if (args.find("--image") == args.end()) {
		printUsage("--image required");
		return -1;
	}
	pathFileImage = args["--image"];
		
	if (args.find("--assets") != args.end()) {
		assetsFolder = args["--assets"];
#if defined(_WIN32)
		std::replace(assetsFolder.begin(), assetsFolder.end(), '\\', '/');
#endif
	}
	if (args.find("--format") != args.end()) {
		format = args["--format"];
	}
	if (args.find("--backprop") != args.end()) {
		backpropEnabled = (args["--backprop"] == "true");
	}
	if (args.find("--ielcd") != args.end()) {
		ielcdEnabled = (args["--ielcd"] == "true");
	}
	if (args.find("--tokenfile") != args.end()) {
		licenseTokenFile = args["--tokenfile"];
#if defined(_WIN32)
		std::replace(licenseTokenFile.begin(), licenseTokenFile.end(), '\\', '/');
#endif
	}
	if (args.find("--tokendata") != args.end()) {
		licenseTokenData = args["--tokendata"];
	}

	// Update JSON config
	std::string jsonConfig = __jsonConfig;
	if (!assetsFolder.empty()) {
		jsonConfig += std::string(",\"assets_folder\": \"") + assetsFolder + std::string("\"");
	}
	if (!format.empty()) {
		jsonConfig += std::string(",\"format\": \"") + format + std::string("\"");
	}
	jsonConfig += std::string(",\"backpropagation_enabled\": ") + (backpropEnabled ? "true" : "false");
	jsonConfig += std::string(",\"ielcd_enabled\": ") + (ielcdEnabled ? "true" : "false");
	if (!licenseTokenFile.empty()) {
		jsonConfig += std::string(",\"license_token_file\": \"") + licenseTokenFile + std::string("\"");
	}
	if (!licenseTokenData.empty()) {
		jsonConfig += std::string(",\"license_token_data\": \"") + licenseTokenData + std::string("\"");
	}
	
	jsonConfig += "}"; // end-of-config

	// Decode the file
	MicrFile file;
	if (!readFile(pathFileImage, file)) {
		ULTMICR_SDK_PRINT_ERROR("Can't process %s", pathFileImage.c_str());
		return -1;
	}
	ULTMICR_SDK_ASSERT(file.isValid());

	// Init
	ULTMICR_SDK_PRINT_INFO("Starting recognizer...");
	ULTMICR_SDK_ASSERT((result = UltMicrSdkEngine::init(
		ASSET_MGR_PARAM()
		jsonConfig.c_str()
	)).isOK());

	// Recognize/Process
	ULTMICR_SDK_ASSERT((result = UltMicrSdkEngine::process(
		file.channels == 4 ? ULTMICR_SDK_IMAGE_TYPE_RGBA32 : (file.channels == 1 ? ULTMICR_SDK_IMAGE_TYPE_Y : ULTMICR_SDK_IMAGE_TYPE_RGB24),
		file.uncompressedDataPtr,
		static_cast<size_t>(file.width),
		static_cast<size_t>(file.height),
		0, // stride
		UltMicrSdkEngine::exifOrientation(file.compressedDataPtr, file.compressedDataSize)
	)).isOK());
	ULTMICR_SDK_PRINT_INFO("Processing done.");

	// Print latest result
	const std::string& json_ = result.json();
	if (!json_.empty()) {
		ULTMICR_SDK_PRINT_INFO("result: %s", json_.c_str());
	}

	ULTMICR_SDK_PRINT_INFO("Press any key to terminate !!");
	getchar();

	// DeInit
	ULTMICR_SDK_PRINT_INFO("Ending recognizer...");
	ULTMICR_SDK_ASSERT((result = UltMicrSdkEngine::deInit()).isOK());

	return 0;
}

/*
* Print usage
*/
static void printUsage(const std::string& message /*= ""*/)
{
	if (!message.empty()) {
		ULTMICR_SDK_PRINT_ERROR("%s", message.c_str());
	}

	ULTMICR_SDK_PRINT_INFO(
		"\n********************************************************************************\n"
		"recognizer\n"
		"\t--image <path-to-image-with-micr-zone-to-recognize> \n"
		"\t[--assets <path-to-assets-folder>] \n"
		"\t[--backprop <whether-to-enable-backpropagation:true/false>] \n"
		"\t[--ielcd <whether-to-enable-IELCD:true/false>] \n"
		"\t[--tokenfile <path-to-license-token-file>] \n"
		"\t[--tokendata <base64-license-token-data>] \n"
		"\n"
		"Options surrounded with [] are optional.\n"
		"\n"
		"--image: Path to the image(JPEG/PNG/BMP) to process. You can use default image at ../../../assets/images/e13b_1280x720.jpg.\n\n"
		"--assets: Path to the assets folder containing the configuration files and models. Default value is the current folder.\n\n"
		"--format: Defines the MICR format to enable for the detection. Use \"e13b\" to look for E-13B lines only and \"cmc7\" for CMC-7 lines only. To look for both, use \"e13b+cmc7\". For performance reasons you should not use \"e13b+cmc7\" unless you really expect the document to contain both E-13B and CMC7 lines. Default: \"e13b+cmc7\"\n\n"
		"--backprop: Whether to enable backpropagation to detect the MICR lines. Only CMC-7 font uses this option. More information at https://www.doubango.org/SDKs/micr/docs/Detection_techniques.html#backpropagation. Default: true for x86 CPUs and false for ARM CPUs.\n\n"
		"--ielcd: Whether to enable Image Enhancement for Low Contrast Document (IELCD). More information at https://www.doubango.org/SDKs/micr/docs/IELCD.html#ielcd. Default: true for x86 CPUs and false for ARM CPUs.\n\n"
		"--tokenfile: Path to the file containing the base64 license token if you have one. If not provided then, the application will act like a trial version. Default: null.\n\n"
		"--tokendata: Base64 license token if you have one. If not provided then, the application will act like a trial version. Default: null.\n\n"
		"********************************************************************************\n"
	);
}

static bool parseArgs(int argc, char *argv[], std::map<std::string, std::string >& values)
{
	ULTMICR_SDK_ASSERT(argc > 0 && argv != nullptr);

	values.clear();

	// Make sure the number of arguments is even
	if ((argc - 1) & 1) {
		ULTMICR_SDK_PRINT_ERROR("Number of args must be even");
		return false;
	}

	// Parsing
	for (int index = 1; index < argc; index += 2) {
		std::string key = argv[index];
		if (key.size() < 2 || key[0] != '-' || key[1] != '-') {
			ULTMICR_SDK_PRINT_ERROR("Invalid key: %s", key.c_str());
			return false;
		}
		values[key] = argv[index + 1];
	}

	return true;
}

static bool readFile(const std::string& path, MicrFile& file)
{
	// Open the file
	if ((file.filePtr = fopen(path.c_str(), "rb")) == nullptr) {
		ULTMICR_SDK_PRINT_ERROR("Can't open %s", path.c_str());
		return false;
	}

	// Retrieve file size
	struct stat st_;
	if (stat(path.c_str(), &st_) != 0) {
		ULTMICR_SDK_PRINT_ERROR("File is empty %s", path.c_str());
	}
	file.compressedDataSize = static_cast<size_t>(st_.st_size);

	// Alloc memory and read data
	file.compressedDataPtr = ::malloc(file.compressedDataSize);
	if (!file.compressedDataPtr) {
		ULTMICR_SDK_PRINT_ERROR("Failed to alloc mem with size = %zu", file.compressedDataSize);
		return false;
	}
	size_t read_;
	if (file.compressedDataSize != (read_ = fread(file.compressedDataPtr, 1, file.compressedDataSize, file.filePtr))) {
		ULTMICR_SDK_PRINT_ERROR("fread(%s) returned %zu instead of %zu", path.c_str(), read_, file.compressedDataSize);
		return false;
	}

	// Decode image
	file.uncompressedDataPtr = stbi_load_from_memory(
		reinterpret_cast<stbi_uc const *>(file.compressedDataPtr), static_cast<int>(file.compressedDataSize),
		&file.width, &file.height, &file.channels, 0
	);

	return file.isValid();
}
