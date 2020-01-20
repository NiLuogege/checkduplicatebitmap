package com.niluogege.duplicatedbitmapanalyzer;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.ClassObj;
import com.squareup.haha.perflib.Field;
import com.squareup.haha.perflib.Heap;
import com.squareup.haha.perflib.HprofParser;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.RootObj;
import com.squareup.haha.perflib.Snapshot;
import com.squareup.haha.perflib.Type;
import com.squareup.haha.perflib.io.MemoryMappedFileBuffer;
import com.squareup.leakcanary.ExcludedRefs;
import com.squareup.leakcanary.HahaHelper;
import com.squareup.leakcanary.LeakNode;
import com.squareup.leakcanary.LeakReference;
import com.squareup.leakcanary.LeakTrace;
import com.squareup.leakcanary.LeakTraceElement;
import com.squareup.leakcanary.Reachability;
import com.squareup.leakcanary.ShortestPathFinder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import static com.squareup.leakcanary.HahaHelper.extendsThread;
import static com.squareup.leakcanary.HahaHelper.threadName;
import static com.squareup.leakcanary.HahaHelper.valueAsString;
import static com.squareup.leakcanary.LeakTraceElement.Holder.ARRAY;
import static com.squareup.leakcanary.LeakTraceElement.Holder.CLASS;
import static com.squareup.leakcanary.LeakTraceElement.Holder.OBJECT;
import static com.squareup.leakcanary.LeakTraceElement.Holder.THREAD;
import static com.squareup.leakcanary.LeakTraceElement.Type.ARRAY_ENTRY;
import static com.squareup.leakcanary.LeakTraceElement.Type.INSTANCE_FIELD;
import static com.squareup.leakcanary.LeakTraceElement.Type.STATIC_FIELD;
import static com.squareup.leakcanary.Reachability.REACHABLE;
import static com.squareup.leakcanary.Reachability.UNKNOWN;
import static com.squareup.leakcanary.Reachability.UNREACHABLE;

/**
 * Created by niluogege on 2020/1/16.
 */
public class Main {
    private static final String ANONYMOUS_CLASS_NAME_PATTERN = "^.+\\$\\d+$";

    private static Map<String, BitmapWapper> bitmaps = new HashMap<>();
    private static Snapshot snapshot;

    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                MemoryMappedFileBuffer mappedFileBuffer = new MemoryMappedFileBuffer(new File(args[0]));
                HprofParser parser = new HprofParser(mappedFileBuffer);
                // 获得snapshot
                snapshot = parser.parse();
                // 获得Bitmap Class
                ClassObj bitmapClass = snapshot.findClass("android.graphics.Bitmap");
                //获得heap, 只需要分析app和default heap即可
                Collection<Heap> heaps = snapshot.getHeaps();
                for (Heap heap : heaps) {
                    if (heap.getName().equals("app")) {

                        //从heap中获得所有的bitmap
                        List<Instance> bitmapInstances = bitmapClass.getHeapInstances(heap.getId());

                        for (Instance bitmapInstance : bitmapInstances) {

                            List<ClassInstance.FieldValue> values = ((ClassInstance) bitmapInstance).getValues();
                            // 从Bitmap实例中获得buffer数组，需要注意 8.0以后 bitmap存放到了 native中 所以没有 mBuffer 成员属性
                            ArrayInstance buffer = HahaHelper.fieldValue(values, "mBuffer");

                            String md5 = MD5Utils.getMD5(buffer.getValues());
                            if (bitmaps.containsKey(md5)) {//重复
                                BitmapWapper wapper = bitmaps.get(md5);
                                wapper.instances.add(bitmapInstance);
                                wapper.count += 1;
                            } else {//不重复
                                BitmapWapper wapper = new BitmapWapper();
                                ArrayList<Instance> instances = new ArrayList<>();
                                instances.add(bitmapInstance);
                                wapper.instances = instances;
                                wapper.count = 1;
                                bitmaps.put(md5, wapper);
                            }
                        }

                    }
                }


                print();

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("请传入hprof文件路径");
        }
    }

//    private static String getStack(Instance instance) {
//
//        String stacks = "";
//
//        ExcludedRefs NO_EXCLUDED_REFS = ExcludedRefs.builder().build();
//        HeapAnalyzer heapAnalyzer = new HeapAnalyzer(NO_EXCLUDED_REFS, AnalyzerProgressListener.NONE, new ArrayList<>());
//        Class<? extends HeapAnalyzer> heapAnalyzerClass = heapAnalyzer.getClass();
//
//        try {
//            Method method = heapAnalyzerClass.getDeclaredMethod("findLeakTrace",
//                    long.class,
//                    Snapshot.class,
//                    Instance.class,
//                    boolean.class);
//
//            method.setAccessible(true);
//
//            AnalysisResult analysisResult = (AnalysisResult) method.invoke(heapAnalyzer, System.nanoTime(), snapshot, instance, false);
//            stacks = analysisResult.leakTrace.toString();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return stacks;
//
//    }


    private static String getStack(Instance instance) {
        ExcludedRefs excludedRefs = ExcludedRefs.builder().build();
        //最短引用查找器
        ShortestPathFinder pathFinder = new ShortestPathFinder(excludedRefs);
        //查找到了 泄漏点也找到了 引用关系
        ShortestPathFinder.Result result = pathFinder.findPath(snapshot, instance);

        //将 leakingNode 转换为 LeakTrace
        LeakTrace leakTrace = buildLeakTrace(result.leakingNode);

//        return leakTrace.toDetailedString();
        return leakTrace.toString();
    }

    private static LeakTrace buildLeakTrace(LeakNode leakingNode) {
        List<LeakTraceElement> elements = new ArrayList<>();
        // We iterate from the leak to the GC root
        LeakNode node = new LeakNode(null, null, leakingNode, null);
        while (node != null) {
            LeakTraceElement element = buildLeakElement(node);
            if (element != null) {
                elements.add(0, element);
            }
            node = node.parent;
        }

        List<Reachability> expectedReachability = computeExpectedReachability(elements);

        return new LeakTrace(elements, expectedReachability);
    }

    private static LeakTraceElement buildLeakElement(LeakNode node) {
        if (node.parent == null) {
            // Ignore any root node.
            return null;
        }
        Instance holder = node.parent.instance;

        if (holder instanceof RootObj) {
            return null;
        }
        LeakTraceElement.Holder holderType;
        String className;
        String extra = null;
        List<LeakReference> leakReferences = describeFields(holder);

        className = getClassName(holder);

        List<String> classHierarchy = new ArrayList<>();
        classHierarchy.add(className);
        String rootClassName = Object.class.getName();
        if (holder instanceof ClassInstance) {
            ClassObj classObj = holder.getClassObj();
            while (!(classObj = classObj.getSuperClassObj()).getClassName().equals(rootClassName)) {
                classHierarchy.add(classObj.getClassName());
            }
        }

        if (holder instanceof ClassObj) {
            holderType = CLASS;
        } else if (holder instanceof ArrayInstance) {
            holderType = ARRAY;
        } else {
            ClassObj classObj = holder.getClassObj();
            if (extendsThread(classObj)) {
                holderType = THREAD;
                String threadName = threadName(holder);
                extra = "(named '" + threadName + "')";
            } else if (className.matches(ANONYMOUS_CLASS_NAME_PATTERN)) {
                String parentClassName = classObj.getSuperClassObj().getClassName();
                if (rootClassName.equals(parentClassName)) {
                    holderType = OBJECT;
                    try {
                        // This is an anonymous class implementing an interface. The API does not give access
                        // to the interfaces implemented by the class. We check if it's in the class path and
                        // use that instead.
                        Class<?> actualClass = Class.forName(classObj.getClassName());
                        Class<?>[] interfaces = actualClass.getInterfaces();
                        if (interfaces.length > 0) {
                            Class<?> implementedInterface = interfaces[0];
                            extra = "(anonymous implementation of " + implementedInterface.getName() + ")";
                        } else {
                            extra = "(anonymous subclass of java.lang.Object)";
                        }
                    } catch (ClassNotFoundException ignored) {
                    }
                } else {
                    holderType = OBJECT;
                    // Makes it easier to figure out which anonymous class we're looking at.
                    extra = "(anonymous subclass of " + parentClassName + ")";
                }
            } else {
                holderType = OBJECT;
            }
        }
        return new LeakTraceElement(node.leakReference, holderType, classHierarchy, extra,
                node.exclusion, leakReferences);
    }

    private static List<Reachability> computeExpectedReachability(List<LeakTraceElement> elements) {
        return new ArrayList<>();
    }

    private static List<LeakReference> describeFields(Instance instance) {
        List<LeakReference> leakReferences = new ArrayList<>();
        if (instance instanceof ClassObj) {
            ClassObj classObj = (ClassObj) instance;
            for (Map.Entry<Field, Object> entry : classObj.getStaticFieldValues().entrySet()) {
                String name = entry.getKey().getName();
                String stringValue = valueAsString(entry.getValue());
                leakReferences.add(new LeakReference(STATIC_FIELD, name, stringValue));
            }
        } else if (instance instanceof ArrayInstance) {
            ArrayInstance arrayInstance = (ArrayInstance) instance;
            if (arrayInstance.getArrayType() == Type.OBJECT) {
                Object[] values = arrayInstance.getValues();
                for (int i = 0; i < values.length; i++) {
                    String name = Integer.toString(i);
                    String stringValue = valueAsString(values[i]);
                    leakReferences.add(new LeakReference(ARRAY_ENTRY, name, stringValue));
                }
            }
        } else {
            ClassObj classObj = instance.getClassObj();
            for (Map.Entry<Field, Object> entry : classObj.getStaticFieldValues().entrySet()) {
                String name = entry.getKey().getName();
                String stringValue = valueAsString(entry.getValue());
                leakReferences.add(new LeakReference(STATIC_FIELD, name, stringValue));
            }
            ClassInstance classInstance = (ClassInstance) instance;
            for (ClassInstance.FieldValue field : classInstance.getValues()) {
                String name = field.getField().getName();
                String stringValue = valueAsString(field.getValue());
                leakReferences.add(new LeakReference(INSTANCE_FIELD, name, stringValue));
            }
        }
        return leakReferences;
    }

    private static String getClassName(Instance instance) {
        String className;
        if (instance instanceof ClassObj) {
            ClassObj classObj = (ClassObj) instance;
            className = classObj.getClassName();
        } else if (instance instanceof ArrayInstance) {
            ArrayInstance arrayInstance = (ArrayInstance) instance;
            className = arrayInstance.getClassObj().getClassName();
        } else {
            ClassObj classObj = instance.getClassObj();
            className = classObj.getClassName();
        }
        return className;
    }

    private static void print() throws Exception {
        for (String md5 : bitmaps.keySet()) {
            BitmapWapper wapper = bitmaps.get(md5);
            if (wapper.count > 1) {

                StringBuffer sb = new StringBuffer();

                sb.append("duplcateCount").append(":").append(wapper.count).append("\n");
                sb.append("bufferHash").append(":").append(md5).append("\n");

                for (Instance instance : wapper.instances) {
                    ArrayInstance buffer = HahaHelper.fieldValue(((ClassInstance) instance).getValues(), "mBuffer");
                    Integer width = HahaHelper.fieldValue(((ClassInstance) instance).getValues(), "mWidth");
                    Integer height = HahaHelper.fieldValue(((ClassInstance) instance).getValues(), "mHeight");

                    sb.append("\t").append("width").append(":").append(width).append("\n");
                    sb.append("\t").append("height").append(":").append(height).append("\n");
                    sb.append("\t").append("bufferSize").append(":").append(buffer.getSize()).append("\n");
                    sb.append("\t").append("stacks").append(":").append(getStack(instance)).append("\n");
                    sb.append("--------------------").append("\n");

                    generateImage(md5, buffer, width, height);
                }
                System.out.println(sb.toString());
            }

        }
    }

    /**
     * 生成图片
     *
     * @param md5
     * @param buffer
     * @param width
     * @param height
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws IOException
     */
    private static void generateImage(String md5, ArrayInstance buffer, Integer width, Integer height) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, IOException {
        Class<? extends ArrayInstance> bufferClass = buffer.getClass();
        Method asRawByteArray = bufferClass.getDeclaredMethod("asRawByteArray", int.class, int.class);
        asRawByteArray.setAccessible(true);
        byte[] data = (byte[]) asRawByteArray.invoke(buffer, 0, buffer.getValues().length);
        String filePath = "E:\\111work\\code\\code_me\\myGitHub\\checkduplicatebitmap\\checkduplicatebitmap\\" + md5 + ".png";
        ARGB8888_BitmapExtractor.getImage(width, height, data, filePath);
    }

    /**
     * 根据 byte[] 保存图片到文件
     * 参考
     * https://github.com/JetBrains/adt-tools-base/blob/master/ddmlib/src/main/java/com/android/ddmlib/BitmapDecoder.java
     */
    private static class ARGB8888_BitmapExtractor {

        public static void getImage(int width, int height, byte[] rgba, String pngFilePath) throws IOException {
            BufferedImage bufferedImage = new BufferedImage(width, height,
                    BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < height; y++) {
                int stride = y * width;
                for (int x = 0; x < width; x++) {
                    int i = (stride + x) * 4;
                    long rgb = 0;
                    rgb |= ((long) rgba[i] & 0xff) << 16; // r
                    rgb |= ((long) rgba[i + 1] & 0xff) << 8;  // g
                    rgb |= ((long) rgba[i + 2] & 0xff);       // b
                    rgb |= ((long) rgba[i + 3] & 0xff) << 24; // a
                    bufferedImage.setRGB(x, y, (int) (rgb & 0xffffffffl));
                }
            }
            File outputfile = new File(pngFilePath);
            ImageIO.write(bufferedImage, "png", outputfile);

        }
    }
}
