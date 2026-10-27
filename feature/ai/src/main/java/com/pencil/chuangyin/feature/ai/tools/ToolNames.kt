package com.pencil.chuangyin.feature.ai.tools

/** 工具名称常量（同时作为 OpenAI function calling 的 function name）。 */
object ToolNames {
    // 存储类
    const val LIST_FILES = "list_files"
    const val READ_FILE = "read_file"
    const val WRITE_FILE = "write_file"
    const val DELETE_FILE = "delete_file"
    const val SEARCH_FILES = "search_files"
    const val MAKE_DIR = "make_dir"
    const val COPY_FILE = "copy_file"
    const val MOVE_FILE = "move_file"
    const val FILE_INFO = "file_info"

    // 压缩包类
    const val EXTRACT_ARCHIVE = "extract_archive"
    const val CREATE_ARCHIVE = "create_archive"

    // 图片类
    const val EDIT_IMAGE = "edit_image"

    // 视频类
    const val VIDEO_INFO = "video_info"
    const val EXTRACT_VIDEO_FRAMES = "extract_video_frames"

    // 打印类
    const val PRINT_TEXT = "print_text"
    const val PRINT_TABLE = "print_table"
    const val PRINT_IMAGE = "print_image"
    const val PRINT_CONVERSATION = "print_conversation"
    const val PRINTER_STATUS = "printer_status"
    const val TEST_FEED = "test_feed"
    const val DRAW_IMAGE = "draw_image"

    // 应用控制类
    const val APP_NAVIGATE = "app_navigate"
    const val APP_LIST_DESTINATIONS = "app_list_destinations"

    // 联网类
    const val WEB_SEARCH = "web_search"
    const val WEB_FETCH = "web_fetch"

    // 终端类
    const val RUN_COMMAND = "run_command"
    const val SHELL_SETUP = "shell_setup"
    const val SHELL_STATUS = "shell_status"

    // P 站类（由 feature:pixiv 注册）
    const val PIXIV_HOME_FEED = "pixiv_home_feed"
    const val PIXIV_SEARCH_ILLUST = "pixiv_search_illust"
    const val PIXIV_ILLUST_DETAIL = "pixiv_illust_detail"
    const val PIXIV_DOWNLOAD_ILLUST = "pixiv_download_illust"
    const val PIXIV_SEARCH_NOVEL = "pixiv_search_novel"
    const val PIXIV_DOWNLOAD_NOVEL = "pixiv_download_novel"
}

/** 工具参数名常量。 */
object ToolParams {
    const val PATH = "path"
    const val CONTENT = "content"
    const val QUERY = "query"
    const val TITLE = "title"
    const val FONT_SIZE = "font_size"
    const val ALIGN = "align"
    const val BOLD = "bold"
    const val ALGORITHM = "algorithm"
    const val THRESHOLD = "threshold"
    const val ROTATION = "rotation"
    const val WIDTH = "width"
    const val HEIGHT = "height"
    const val BACKGROUND = "background"
    const val SHAPES = "shapes"
    const val PRINT_AFTER_DRAW = "print"
    const val FILE_NAME = "file_name"

    // 文件操作
    const val SOURCE = "source"
    const val DESTINATION = "destination"
    const val SOURCES = "sources"

    // 图片处理
    const val OPERATION = "operation"
    const val SCALE = "scale"
    const val DEGREES = "degrees"
    const val CROP_X = "crop_x"
    const val CROP_Y = "crop_y"
    const val CROP_WIDTH = "crop_width"
    const val CROP_HEIGHT = "crop_height"
    const val FLIP_HORIZONTAL = "flip_horizontal"
    const val FORMAT = "format"
    const val QUALITY = "quality"

    // 表格
    const val HEADERS = "headers"
    const val ROWS = "rows"

    // 应用控制
    const val PAGE_KEY = "page"
    const val DEST_KEY = "destination_key"
    const val ARGS = "args"

    // 联网 / 终端
    const val URL = "url"
    const val COMMAND = "command"
    const val CWD = "cwd"
    const val TIMEOUT_MS = "timeout_ms"
    const val MAX_CHARS = "max_chars"
    const val MAX_RESULTS = "max_results"

    // P 站
    const val WORD = "word"
    const val SORT = "sort"
    const val TARGET = "target"
    const val MODE = "mode"
    const val LANG = "lang"
    const val LIMIT = "limit"
    const val ILLUST_ID = "illust_id"
    const val NOVEL_ID = "novel_id"
    const val PAGE_INDEX = "page_index"
}

/** 参数取值常量。 */
object ToolParamValues {
    const val ALIGN_START = "start"
    const val ALIGN_CENTER = "center"
    const val ALIGN_END = "end"
    val ALIGN_VALUES = listOf(ALIGN_START, ALIGN_CENTER, ALIGN_END)

    /** 与 [com.pencil.chuangyin.printer.BinarizeAlgorithm] 枚举名严格一致。 */
    val BINARIZE_VALUES = listOf(
        "FixedThreshold",
        "FloydSteinberg",
        "Atkinson",
        "SobelEdge",
        "SauvolaAdaptive",
    )

    /** draw_image 支持的图形类型。 */
    val SHAPE_TYPES = listOf(
        "rect",
        "round_rect",
        "circle",
        "ellipse",
        "line",
        "polygon",
        "polyline",
        "text",
    )

    // 图片处理操作
    const val OP_RESIZE = "resize"
    const val OP_THUMBNAIL = "thumbnail"
    const val OP_ROTATE = "rotate"
    const val OP_CROP = "crop"
    const val OP_GRAYSCALE = "grayscale"
    const val OP_FLIP = "flip"
    const val OP_CONVERT = "convert"
    val IMAGE_OPERATIONS = listOf(
        OP_RESIZE,
        OP_THUMBNAIL,
        OP_ROTATE,
        OP_CROP,
        OP_GRAYSCALE,
        OP_FLIP,
        OP_CONVERT,
    )

    // 图片格式
    const val FORMAT_PNG = "png"
    const val FORMAT_JPEG = "jpeg"
    const val FORMAT_WEBP = "webp"
    val IMAGE_FORMATS = listOf(FORMAT_PNG, FORMAT_JPEG, FORMAT_WEBP)

    // P 站排序 / 检索目标（取值与 P 站 API 参数一致）
    val PIXIV_SORTS = listOf("date_desc", "date_asc")
    val PIXIV_TARGETS = listOf(
        "partial_match_for_tags",
        "exact_match_for_tags",
        "title_and_caption",
    )
}
