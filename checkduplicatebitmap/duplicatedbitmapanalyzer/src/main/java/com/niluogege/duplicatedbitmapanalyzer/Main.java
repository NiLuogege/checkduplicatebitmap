package com.niluogege.duplicatedbitmapanalyzer;

import com.alibaba.fastjson.JSONObject;
import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.ClassObj;
import com.squareup.haha.perflib.Heap;
import com.squareup.haha.perflib.HprofParser;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.Snapshot;
import com.squareup.haha.perflib.io.MemoryMappedFileBuffer;
import com.squareup.leakcanary.AnalysisResult;
import com.squareup.leakcanary.AnalyzerProgressListener;
import com.squareup.leakcanary.ExcludedRefs;
import com.squareup.leakcanary.HeapAnalyzer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by niluogege on 2020/1/16.
 */
public class Main {
    private static Map<String, BitmapWapper> bitmaps = new HashMap<>();
    private static Snapshot snapshot;

    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                MemoryMappedFileBuffer mappedFileBuffer = new MemoryMappedFileBuffer(new File(args[0]));
                HprofParser parser = new HprofParser(mappedFileBuffer);
                // 获得snapshot
                snapshot =parser.parse();
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
                                wapper.count += 1;
                            } else {//不重复
                                BitmapWapper wapper = new BitmapWapper();
                                wapper.instance = bitmapInstance;
                                wapper.count = 1;
                                bitmaps.put(md5, wapper);
                            }
                        }

                    }
                }


                print();

            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("请传入hprof文件路径");
        }
    }

    private static String getStack(Instance instance) {

        String stacks = "";

        ExcludedRefs NO_EXCLUDED_REFS = ExcludedRefs.builder().build();
        HeapAnalyzer heapAnalyzer = new HeapAnalyzer(NO_EXCLUDED_REFS, AnalyzerProgressListener.NONE, new ArrayList<>());
        Class<? extends HeapAnalyzer> heapAnalyzerClass = heapAnalyzer.getClass();

        try {
            Method method = heapAnalyzerClass.getDeclaredMethod("findLeakTrace",
                    long.class,
                    Snapshot.class,
                    Instance.class,
                    boolean.class);

            method.setAccessible(true);

            AnalysisResult analysisResult = (AnalysisResult) method.invoke(heapAnalyzer, System.nanoTime(), snapshot, instance, false);
            stacks = analysisResult.leakTrace.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return stacks;

    }

    private static void print() {
        for (String md5 : bitmaps.keySet()) {
            BitmapWapper wapper = bitmaps.get(md5);
            if (wapper.count > 1) {
                ArrayInstance buffer = HahaHelper.fieldValue(((ClassInstance) wapper.instance).getValues(), "mBuffer");
                Object width = HahaHelper.fieldValue(((ClassInstance) wapper.instance).getValues(), "mWidth");
                Object height = HahaHelper.fieldValue(((ClassInstance) wapper.instance).getValues(), "mHeight");

                JSONObject object = new JSONObject();
                object.put("duplcateCount", wapper.count);
                object.put("bufferHash", md5);
                object.put("width", width);
                object.put("height", height);
                object.put("bufferSize", buffer.getSize());
                object.put("stacks", getStack(wapper.instance));

                System.out.println(JsonUtil.formatJson(object.toJSONString()));
            }

        }
    }
}
