package com.navith.amap;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Import an official AMAP offline style ZIP; never treat an arbitrary file as a map. */
public final class MapStyleImporter {
    private MapStyleImporter(){}
    private static final String[] ALLOWED={"style.data","style_extra.data","textures.zip"};
    public static File directory(Context c){return new File(c.getFilesDir(),"amap-custom-style");}
    public static File styleFile(Context c){return new File(directory(c),"style.data");}
    public static boolean installed(Context c){return styleFile(c).isFile();}
    public static void clear(Context c){
        for(String name:ALLOWED){File f=new File(directory(c),name);if(f.exists())f.delete();}
    }
    public static void install(Context c,Uri uri)throws IOException {
        File dir=directory(c);
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create style directory");
        // Copy only these 3 expected files, never arbitrary paths in an archive.
        File[] pending=new File[ALLOWED.length];
        for(int i=0;i<ALLOWED.length;i++)pending[i]=new File(dir,ALLOWED[i]+".tmp");
        for(File f:pending)if(f.exists())f.delete();
        boolean foundStyle=false;
        long total=0;
        try(InputStream source=c.getContentResolver().openInputStream(uri);
            ZipInputStream zip=new ZipInputStream(source)){
            if(source==null)throw new IOException("Cannot open file");
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(entry.isDirectory())continue;
                String name=entry.getName().replace('\\','/');
                int slash=name.lastIndexOf('/');
                if(slash>=0)name=name.substring(slash+1);
                int index=-1;
                for(int i=0;i<ALLOWED.length;i++)if(ALLOWED[i].equals(name))index=i;
                if(index<0)continue;
                try(FileOutputStream dest=new FileOutputStream(pending[index])){
                    byte[] buffer=new byte[8192];int n;
                    while((n=zip.read(buffer))!=-1){
                        total+=n;
                        if(total>12L*1024*1024)throw new IOException("Map style archive exceeds 12 MB");
                        dest.write(buffer,0,n);
                    }
                }
                if(index==0)foundStyle=true;
                zip.closeEntry();
            }
        }catch(IOException | RuntimeException e){
            for(File f:pending)f.delete();
            throw e;
        }
        if(!foundStyle||pending[0].length()==0)throw new IOException("style.data missing");
        for(int i=0;i<ALLOWED.length;i++){
            File dest=new File(dir,ALLOWED[i]);
            if(pending[i].exists()){
                if(dest.exists()&&!dest.delete())throw new IOException("Cannot replace style");
                if(!pending[i].renameTo(dest))throw new IOException("Cannot save style");
            } else if(dest.exists())dest.delete();
        }
    }
}
