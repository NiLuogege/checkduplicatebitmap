# checkduplicatebitmap
检测内存中的重复bitmap对象，和对象引用的堆栈,并通过 bitmap的buffer还原 图片。

# 注意
1. android8.0以后 bitmap存到了native 中，所以Bitmap 对象中不存在 mBuffer成员变量了。所以如果需要测试 ，可以用 该项目根目录下的b.hprof文件做测试。
