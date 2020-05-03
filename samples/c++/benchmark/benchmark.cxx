/* Copyright (C) 2011-2020 Doubango Telecom <https://www.doubango.org>
* File author: Mamadou DIOP (Doubango Telecom, France).
* License: For non commercial use only.
* Source code: https://github.com/DoubangoTelecom/ultimateMICR-SDK
* WebSite: https://www.doubango.org/webapps/micr/
*/

// More info about benchmark application: https://www.doubango.org/SDKs/micr/docs/Benchmark.html
/*
	https://github.com/DoubangoTelecom/ultimateMICR/blob/master/SDK_dist/samples/c++/benchmark/README.md
	Usage: 
		benchmark \
			--positive <path-to-image-with-micr-zone> \
			--negative <path-to-image-without-micr-zone> \
			[--assets <path-to-assets-folder>] \
			[--format <format-for-dtection:e13b/cmc7/e13b+cmc7>] \
			[--loops <number-of-times-to-run-the-loop:[1, inf]>] \
			[--rate <positive-rate:[0.0, 1.0]>] \
			[--tokenfile <path-to-license-token-file>] \
			[--tokendata <base64-license-token-data>]

	Example:
		benchmark \
			--positive C:/Projects/GitHub/ultimate/ultimateMICR/SDK_dist/assets/images/e13b_1280x720.jpg \
			--negative C:/Projects/GitHub/ultimate/ultimateMICR/SDK_dist/assets/images/traffic_1280x720.jpg \
			--loops 100 \
			--rate 0.2 \
			--assets C:/Projects/GitHub/ultimate/ultimateMICR/SDK_dist/assets \
			--tokenfile C:/Projects/GitHub/ultimate/ultimateMICR/SDK_dev/tokens/windows-iMac.lic
		
*/

#include <ultimateMICR-SDK-API-PUBLIC.h>
#include "../micr_utils.h"
#include <chrono>
#include <vector>
#include <algorithm>
#include <random>
#if defined(_WIN32)
#include <algorithm> // std::replace
#endif

using namespace ultimateMicrSdk;

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
"\"segmenter_accuracy\": \"high\","
"\"interpolation\": \"bilinear\","
"\"roi\": [0, 0, 0, 0],"
"\"min_score\": 0.3,"
"\"score_type\": \"min\""
"";

// Asset manager used on Android to files in "assets" folder
#if ULTMICR_SDK_OS_ANDROID 
#	define ASSET_MGR_PARAM() __sdk_android_assetmgr, 
#else
#	define ASSET_MGR_PARAM() 
#endif /* ULTMICR_SDK_OS_ANDROID */

static void printUsage(const std::string& message = "");

/*
* Entry point
*/
int main(int argc, char *argv[])
{
	// local variables
	UltMicrSdkResult result(0, "OK", "{}");
	std::string assetsFolder, format = "e13b+cmc7", licenseTokenData, licenseTokenFile;
	size_t loopCount = 100;
	double percentPositives = .2; // 20%
	std::string pathFilePositive;
	std::string pathFileNegative;

	// Parsing args
	std::map<std::string, std::string > args;
	if (!micrParseArgs(argc, argv, args)) {
		printUsage();
		return -1;
	}
	if (args.find("--positive") == args.end()) {
		printUsage("--positive required");
		return -1;
	}
	if (args.find("--negative") == args.end()) {
		printUsage("--negative required");
		return -1;
	}
	pathFilePositive = args["--positive"];
	pathFileNegative = args["--negative"];
	if (args.find("--rate") != args.end()) {
		const double rate = std::atof(args["--rate"].c_str());
		if (rate > 1.0 || rate < 0.0) {
			printUsage("--rate must be within [0.0, 1.0]");
			return -1;
		}
		percentPositives = rate;
	}
	if (args.find("--loops") != args.end()) {
		const int loops = std::atoi(args["--loops"].c_str());
		if (loops < 1) {
			printUsage("--loops must be within [1, inf]");
			return -1;
		}
		loopCount = static_cast<size_t>(loops);
	}
	if (args.find("--assets") != args.end()) {
		assetsFolder = args["--assets"];
#if defined(_WIN32)
		std::replace(assetsFolder.begin(), assetsFolder.end(), '\\', '/');
#endif
	}
	if (args.find("--format") != args.end()) {
		format = args["--format"];
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
	if (!licenseTokenFile.empty()) {
		jsonConfig += std::string(",\"license_token_file\": \"") + licenseTokenFile + std::string("\"");
	}
	if (!licenseTokenData.empty()) {
		jsonConfig += std::string(",\"license_token_data\": \"") + licenseTokenData + std::string("\"");
	}
	
	jsonConfig += "}"; // end-of-config

	// Read files
	// Positive: the file contains at least one MICR line
	// Negative: the file doesn't contain a MICR lines
	// Change positive rates to evaluate the detector versus recognizer
	MicrFile filePositive, fileNegative;
	if (!micrDecodeFile(pathFilePositive, filePositive)) {
		ULTMICR_SDK_PRINT_INFO("Failed to read positive file: %s", pathFilePositive.c_str());
		return -1;
	}
	if (!micrDecodeFile(pathFileNegative, fileNegative)) {
		ULTMICR_SDK_PRINT_INFO("Failed to read positive file: %s", pathFilePositive.c_str());
		return -1;
	}

	// Create image indices
	std::vector<size_t> indices(loopCount, 0);
	const int numPositives = (int)std::max(loopCount * percentPositives, 1.);
	for (int i = 0; i < numPositives; ++i) {
		indices[i] = 1; // positive index
	}
	std::shuffle(std::begin(indices), std::end(indices), std::default_random_engine{}); // make the indices random

	// Init
	ULTMICR_SDK_PRINT_INFO("Starting benchmark...");
	ULTMICR_SDK_ASSERT((result = UltMicrSdkEngine::init(
		ASSET_MGR_PARAM()
		jsonConfig.c_str()
	)).isOK());

	// Warm up:
	// First time the SDK is called we'll be loading the models into CPU or GPU and initializing
	// some internal variables -> do not include this part in te timing.
	// The warm up function will make fake inference to force the engine to load the models and init the vars.
	if (loopCount > 1) {
		ULTMICR_SDK_ASSERT((result = UltMicrSdkEngine::warmUp(
			filePositive.type
		)).isOK());
	}

	// Recognize/Process
	const std::chrono::high_resolution_clock::time_point timeStart = std::chrono::high_resolution_clock::now();
	const MicrFile* files[2] = { &fileNegative, &filePositive };
	for (const auto& indice : indices) {
		const MicrFile* file = files[indice];
		ULTMICR_SDK_ASSERT((result = UltMicrSdkEngine::process(
			file->type,
			file->uncompressedData,
			file->width,
			file->height
		)).isOK());
	}
	const std::chrono::high_resolution_clock::time_point timeEnd = std::chrono::high_resolution_clock::now();
	const double elapsedTimeInMillis = std::chrono::duration_cast<std::chrono::duration<double >>(timeEnd - timeStart).count() * 1000.0;
	ULTMICR_SDK_PRINT_INFO("Elapsed time (MICR) = [[[ %lf millis ]]]", elapsedTimeInMillis);

	// Print latest result
	const std::string& json_ = result.json();
	if (!json_.empty()) {
		ULTMICR_SDK_PRINT_INFO("result: %s", json_.c_str());
	}

	// Print estimated frame rate
	const double estimatedFps = 1000.f / (elapsedTimeInMillis / (double)loopCount);
	ULTMICR_SDK_PRINT_INFO("*** elapsedTimeInMillis: %lf, estimatedFps: %lf ***", elapsedTimeInMillis, estimatedFps);

	ULTMICR_SDK_PRINT_INFO("Press any key to terminate !!");
	getchar();

	// DeInit
	ULTMICR_SDK_PRINT_INFO("Ending benchmark...");
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
		"benchmark\n"
		"\t--positive <path-to-image-with-micr-zone> \n"
		"\t--negative <path-to-image-without-micr-zone> \n"
		"\t[--assets <path-to-assets-folder>] \n"
		"\t[--loops <number-of-times-to-run-the-loop:[1, inf]>] \n"
		"\t[--rate <positive-rate:[0.0, 1.0]>] \n"
		"\t[--tokenfile <path-to-license-token-file>] \n"
		"\t[--tokendata <base64-license-token-data>] \n"
		"\n"
		"Options surrounded with [] are optional.\n"
		"\n"
		"--positive: Path to an image(JPEG/PNG/BMP) with MICR lines.This image will be used to evaluate the recognizer. You can use default image at ../../../assets/images/e13b_1280x720.jpg.\n\n"
		"--negative: Path to an image(JPEG/PNG/BMP) without MICR lines.This image will be used to evaluate the decoder. You can use default image at ../../../assets/images/traffic_1280x720.jpg.\n\n"
		"--assets: Path to the assets folder containing the configuration files and models.Default value is the current folder.\n\n"
		"--format: Defines the MICR format to enable for the detection. Use \"e13b\" to look for E-13B lines only and \"cmc7\" for CMC-7 lines only. To look for both, use \"e13b+cmc7\". For performance reasons you should not use \"e13b+cmc7\" unless you really expect the document to contain both E-13B and CMC7 lines. Default: \"e13b+cmc7\"\n\n"
		"--loops: Number of times to run the processing pipeline.\n\n"
		"--rate: Percentage value within[0.0, 1.0] defining the positive rate. The positive rate defines the percentage of images with MICR lines.\n\n"
		"--tokenfile: Path to the file containing the base64 license token if you have one. If not provided then, the application will act like a trial version. Default: null.\n\n"
		"--tokendata: Base64 license token if you have one. If not provided then, the application will act like a trial version. Default: null.\n\n"
		"********************************************************************************\n"
	);
}
