cmake_minimum_required(VERSION 3.0)

project(recognizer VERSION 1.0.0 LANGUAGES CXX C)

#### ultimate Libraries ####
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../SDK_dev/lib build/ultimateMICR/SDK_dev)

include_directories(
	${CMAKE_CURRENT_SOURCE_DIR}/../../../../SDK_dev/lib/include
)

set(recognizer_SOURCES 
	recognizer.cxx
)

###### The executable ######
add_executable(recognizer ${recognizer_SOURCES})

###### 3rd parties libs ######
target_link_libraries(recognizer ${LIB_LINK_SCOPE} ultimate_micr-sdk)
add_dependencies(recognizer ultimate_micr-sdk)

###### Install Libs ######
install(TARGETS recognizer DESTINATION bin)
