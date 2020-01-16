package com.niluogege.duplicatedbitmapanalyzer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by niluogege on 2020/1/15.
 */
public class MD5Utils {

    public static String getMD5(Object[] source) {
        if (source == null || source.length <= 0) {
            return null;
        }
        byte[] bytes = new byte[source.length];
        int i = 0;
        for (Object object : source) {
            if (object instanceof Byte) {
                bytes[i++] = (byte) object;
            }
        }
        StringBuilder sb = new StringBuilder();
        java.security.MessageDigest md5 = null;
        try {
            md5 = java.security.MessageDigest.getInstance("MD5");
            md5.update(bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        if (md5 != null) {
            for (byte b : md5.digest()) {
                sb.append(String.format("%02X", b));
            }
        }
        return sb.toString();
    }

}
