package com.apextechlabs.wakeremote;
import android.content.Context;import org.json.JSONArray;import org.json.JSONObject;import java.util.ArrayList;import java.util.List;
final class EnrollmentStore{
 static final class Target { final String alias,label; Target(String a,String l){alias=a;label=l;} @Override public String toString(){return label;} }
 private final android.content.SharedPreferences prefs;EnrollmentStore(Context c){prefs=c.getSharedPreferences("enrollment_v1",Context.MODE_PRIVATE);}
 void save(String url,String keyId,JSONArray targets)throws Exception{prefs.edit().putString("url",url).putString("key_id",keyId).putString("targets",targets.toString()).putString("selected",targets.getJSONObject(0).getString("alias")).apply();}
 String url(){return prefs.getString("url","https://wol.apextechlabs.com");}String keyId(){return prefs.getString("key_id","main");}String selected(){return prefs.getString("selected","main-pc");}void select(String alias){prefs.edit().putString("selected",alias).apply();}void clear(){prefs.edit().clear().apply();}
 List<Target> targets(){List<Target> result=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("targets","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);result.add(new Target(o.getString("alias"),o.getString("label")));}}catch(Exception ignored){}return result;}
}
