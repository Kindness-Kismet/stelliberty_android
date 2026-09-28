cmake_minimum_required(VERSION 3.22)
project(stelliberty)

# 体积优化
set(STELLIBERTY_C_OPTS -Os -fvisibility=hidden -ffunction-sections -fdata-sections)
set(STELLIBERTY_LINK_OPTS -Wl,--gc-sections -Wl,--strip-all -Wl,--exclude-libs,ALL)

# libstelliberty.so：fork+exec 与子进程判活的 JNI 实现（Android ProcessBuilder 会关掉 TUN fd）
add_library(stelliberty SHARED process_helper.c)
target_link_libraries(stelliberty PRIVATE log)
target_compile_options(stelliberty PRIVATE ${STELLIBERTY_C_OPTS})
target_link_options(stelliberty PRIVATE ${STELLIBERTY_LINK_OPTS})

# libmihomo.so（IMPORTED）：cgo c-shared
set(MIHOMO_CORE_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
add_library(mihomo_imported SHARED IMPORTED)
set_target_properties(mihomo_imported PROPERTIES
        IMPORTED_LOCATION "${MIHOMO_CORE_DIR}/libmihomo.so"
        IMPORTED_SONAME "libmihomo.so")

# libstelliberty_jni.so：薄 JNI 桥
add_library(stelliberty_jni SHARED stelliberty_jni.c)
target_include_directories(stelliberty_jni PRIVATE "${MIHOMO_CORE_DIR}")
target_link_libraries(stelliberty_jni PRIVATE log mihomo_imported)
target_compile_options(stelliberty_jni PRIVATE ${STELLIBERTY_C_OPTS})
target_link_options(stelliberty_jni PRIVATE ${STELLIBERTY_LINK_OPTS})

# libmihomo_runner.so：薄 PIE 可执行 wrapper
add_executable(mihomo_runner mihomo_wrapper.c)
target_link_libraries(mihomo_runner PRIVATE log dl)
set_target_properties(mihomo_runner PROPERTIES
        OUTPUT_NAME "mihomo_runner"
        PREFIX "lib"
        SUFFIX ".so")
target_compile_options(mihomo_runner PRIVATE ${STELLIBERTY_C_OPTS} -fPIE)
target_link_options(mihomo_runner PRIVATE ${STELLIBERTY_LINK_OPTS} -pie -Wl,-z,now -Wl,-z,relro)
