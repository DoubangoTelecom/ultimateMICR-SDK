/* Copyright (C) 2011-2020 Doubango Telecom <https://www.doubango.org>
* File author: Mamadou DIOP (Doubango Telecom, France).
* License: For non commercial use only.
* Source code: https://github.com/DoubangoTelecom/ultimateMICR-SDK
* WebSite: https://www.doubango.org/webapps/credit-card-ocr/
*/

/*
	https://github.com/DoubangoTelecom/ultimateMICR/blob/master/SDK_dist/samples/c++/licenser/README.md
	Usage: 
		licenser \
			[--json <json-output:bool>] \
			[--assets <path-to-assets-folder>]

	Example:
		licenser \
			--json false \
			--assets C:/Projects/GitHub/ultimate/ultimateMICR/SDK_dist/assets
		
*/

#include <ultimateMICR-SDK-API-PUBLIC.h>
#include "../micr_utils.h"

using namespace ultimateMicrSdk;

static void printUsage(const std::string& message = "");

/*
* Entry point
*/
int main(int argc, char *argv[])
{
	// Parsing args
	std::map<std::string, std::string > args;
	if (!micrParseArgs(argc, argv, args)) {
		printUsage();
		return -1;
	}
	bool rawInsteadOfJSON = false;
	if (args.find("--json") != args.end()) {
		rawInsteadOfJSON = (args["--json"].compare("true") != 0);
	}
	std::string jsonConfig;
	if (args.find("--assets") != args.end()) {
		jsonConfig = std::string("{ \"assets_folder\": \"") + args["--assets"] + std::string("\" }");
	}
	else {
		jsonConfig = "{}";
	}

	// Initialize the engine
	ULTMICR_SDK_ASSERT(UltMicrSdkEngine::init(jsonConfig.c_str()).isOK());

	// Request runtime license key
	const UltMicrSdkResult result = UltMicrSdkEngine::requestRuntimeLicenseKey(rawInsteadOfJSON);
	if (result.isOK()) {
		ULTMICR_SDK_PRINT_INFO("\n\n%s\n\n",
			result.json()
		);
	}
	else {
		ULTMICR_SDK_PRINT_ERROR("\n\n*** Failed: code -> %d, phrase -> %s ***\n\n",
			result.code(),
			result.phrase()
		);
	}
	
	ULTMICR_SDK_PRINT_INFO("Press any key to terminate !!");
	getchar();

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
		"licenser\n"
		"\t[--json <json-output:bool>] \n"
		"\n"
		"Options surrounded with [] are optional.\n"
		"\n"
		"--json: Whether to output the runtime license key as JSON string intead of raw string. Default: true.\n"
		"--assets: Path to the assets folder containing the configuration files and models. Default value is the current folder.\n"
		"********************************************************************************\n"
	);
}
