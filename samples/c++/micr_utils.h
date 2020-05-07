#if !defined(_ULTIMATE_MICR_SDK_SAMPLES_UTILS_H_)
#define _ULTIMATE_MICR_SDK_SAMPLES_UTILS_H_

#include <ultimateMICR-SDK-API-PUBLIC.h>
#include <assert.h>
#include <stdlib.h>
#include <map>
#include <sys/stat.h>

// Not part of the SDK, used to decode images -> https://github.com/nothings/stb
#define STB_IMAGE_IMPLEMENTATION
#define STB_IMAGE_STATIC
#include "stb_image.h"

#if ULTMICR_SDK_OS_ANDROID
#include "micr_utils.h"
#endif /* ULTMICR_SDK_OS_ANDROID */

using namespace ultimateMicrSdk;

/*
* File description
*/
struct MicrFile {
	void* uncompressedData = nullptr;
	size_t width = 0;
	size_t height = 0;
	ULTMICR_SDK_IMAGE_TYPE type;

	virtual ~MicrFile() {
		release();
	}

	void release() {
		if (uncompressedData) {
			free(uncompressedData);
			uncompressedData = nullptr;
		}
	}

	inline bool isValid() const {
		return (uncompressedData != nullptr && width && height);
	}
};

/*
* Decodes a JPEG/PNG/BMP file
* @param path 
* @param type 
* @param width
* @param height
* @returns 
*/
static bool micrDecodeFile(const std::string& path, MicrFile& micrFile)
{
	ULTMICR_SDK_ASSERT(!path.empty());

	// Open file
	FILE* file =
#	if ULTMICR_SDK_OS_ANDROID
		sdk_android_asset_fopen(path.c_str(), "rb");
#	else
		fopen(path.c_str(), "rb");
#	endif
	if (!file) {
		ULTMICR_SDK_PRINT_ERROR("Failed to open file at: %s", path.c_str());
		return false;
	}

	// Decode the file
	int width, height, channels;
	stbi_uc* uncompressedData = stbi_load_from_file(file, &width, &height, &channels, 0);
	fclose(file);
	if (!uncompressedData || width <= 0 || height <= 0 || (channels != 1 && channels != 3 && channels != 4)) {
		ULTMICR_SDK_PRINT_ERROR("Invalid file(%s, %d, %d, %d)", path.c_str(), width, height, channels);
		if (uncompressedData) {
			free(uncompressedData);
		}
		return false;
	}

	// We expect RGB-family data from the JPEG/PNG/BMP file
	// If you're using data from your camera then, it should be YUV-family and you don't need
	// to convert to RGB-family.
	// List of supported types: https://www.doubango.org/SDKs/ccard/docs/cpp-api.html#_CPPv4N15ultimateMicrSdk22ULTMICR_SDK_IMAGE_TYPEE
	micrFile.type = (channels == 3) ? ULTMICR_SDK_IMAGE_TYPE_RGB24 : (channels == 1 ? ULTMICR_SDK_IMAGE_TYPE_Y : ULTMICR_SDK_IMAGE_TYPE_RGBA32);
	micrFile.uncompressedData = uncompressedData;
	micrFile.width = static_cast<size_t>(width);
	micrFile.height = static_cast<size_t>(height);

	return true;
}

static bool micrParseArgs(int argc, char *argv[], std::map<std::string, std::string >& values)
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


#endif /* _ULTIMATE_MICR_SDK_SAMPLES_UTILS_H_ */
