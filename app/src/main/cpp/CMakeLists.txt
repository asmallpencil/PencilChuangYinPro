cmake_minimum_required(VERSION 3.22.1)
project(liquidglass)

add_library(lgm SHARED)

file(GLOB LG_CPP lg/*.cpp)
file(GLOB IMGUI_CPP
    imgui/imgui.cpp
    imgui/imgui_draw.cpp
    imgui/imgui_tables.cpp
    imgui/imgui_widgets.cpp
    imgui/backends/imgui_impl_opengl3.cpp
)

target_sources(lgm PRIVATE
    native_glass.cpp
    ${LG_CPP}
    ${IMGUI_CPP}
)

target_compile_definitions(lgm PRIVATE
    IMGUI_DISABLE_OBSOLETE_FUNCTIONS=1
    IMGUI_IMPL_OPENGL_ES3=1
)

target_include_directories(lgm PRIVATE
    lg
    imgui
    imgui/backends
)

target_link_libraries(lgm
    android
    log
    GLESv3
    EGL
    jnigraphics
    z
)

set_target_properties(lgm PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)