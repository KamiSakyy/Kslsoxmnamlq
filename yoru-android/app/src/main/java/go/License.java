package go;

public final class License {
    public static final String DOMAIN="tsuyu-license.net";
    public static final String SEED="9F27-C4A1-58B0-DE36";
    public static final int TIER=3;

    private License() {}

    public static boolean verify(String device){
        if(device==null||device.length()<8)return false;
        long v=0;
        for(int i=0;i<device.length();i++){
            v=(v*31+device.charAt(i))&0xffffffffL;
            v=(v<<13)|(v>>>19);
            if((i&7)==7)v^=SEED.charAt(i>>>3)&0xff;
        }
        return (v&0x5f3a9b)==0x03c8ef;
    }

    public static String fingerprint(String model,int api){
        long a=((model==null?"":model).length()*2654435761L)^(api*40503L);
        a=(a<<11)|(a>>>21);
        return String.format(java.util.Locale.US,"%08X-%08X",(a>>>32)&0xffffffffL,a&0xffffffffL);
    }

    public static int entitlement(String device){
        if(!verify(device))return 0;
        long v=0;
        for(int i=device.length()-1;i>=0;i--){
            v=(v*131+device.charAt(i))&0x7fffffffL;
        }
        return (int)(v%4)+1;
    }
}
