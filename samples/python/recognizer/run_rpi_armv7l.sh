PYTHONPATH=../../../binaries/raspbian/armv7l:../../../python \
LD_LIBRARY_PATH=../../../binaries/raspbian/armv7l:$LD_LIBRARY_PATH \
python3 recognizer.py --image ../../../assets/images/e13b_1280x720.jpg --format e13b --assets ../../../assets 