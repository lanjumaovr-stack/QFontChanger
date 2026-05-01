import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import me.yxp.qfun.utils.hook.HookExtensionsKt;
import me.yxp.qfun.plugin.loader.PluginManager;
import java.util.Map;
import java.lang.reflect.Method;
import java.io.File;
import java.util.*;

pluginPath = pluginPath.trim();

// ==================== 颜色常量 ====================
static final int C_PRIMARY = Color.parseColor("#3B71FE");
static final int C_PRIMARY_LIGHT = Color.parseColor("#E8EDFF");
static final int C_ON_PRIMARY = Color.WHITE;
static final int C_SURFACE = Color.WHITE;
static final int C_SURFACE_VAR = Color.parseColor("#F5F7FA");
static final int C_ON_SURFACE = Color.parseColor("#1C1B1F");
static final int C_ON_SURFACE_VAR = Color.parseColor("#49454F");
static final int C_OUTLINE = Color.parseColor("#D9D9D9");
static final int C_SELECTED = Color.parseColor("#EEF1FF");
static final int C_TEXT_SEC = Color.parseColor("#8E8E93");

// ==================== 字体引擎 ====================
Typeface[] customTf = new Typeface[1];
String currentFontPath = null;
List<String> fontList = new ArrayList<>();
List<String> fontPathList = new ArrayList<>();
Set<Object> unhookSet = Collections.synchronizedSet(new HashSet<>());
Handler h = new Handler(Looper.getMainLooper());
String currentMenuLabel = "🔤 字体替换";

// ===== 卡顿字符屏蔽 =====
boolean lagFilter = true;
static final int MAX_LEN = 200;
static final int MAX_RATIO = 10;
Set<Integer> noGlyphCache = Collections.synchronizedSet(new HashSet<Integer>());

boolean isInvisible(int cp) {
    if (cp == 0x200B||cp==0x200C||cp==0x200D||cp==0x200E||cp==0x200F||cp==0xFEFF) return true;
    if (cp>=0x2060&&cp<=0x2064) return true;
    if (cp>=0xFE00&&cp<=0xFE0F||cp>=0xE0100&&cp<=0xE01EF) return true;
    if (cp>=0x0300&&cp<=0x036F||cp>=0x1AB0&&cp<=0x1AFF||cp>=0x1DC0&&cp<=0x1DFF) return true;
    if (cp>=0x20D0&&cp<=0x20FF||cp>=0xFE20&&cp<=0xFE2F) return true;
    if (cp>=0xE0001&&cp<=0xE007F) return true;
    if (cp<=0x001F||cp>=0x007F&&cp<=0x009F) return true;
    if (cp==0x2066||cp==0x2067||cp==0x2068||cp==0x2069) return true;
    if (cp==0xFFFC||cp==0xFFFD) return true;
    return false;
}

int countVisible(CharSequence t) {
    if (t==null||t.length()==0) return 0;
    int c=0, len=t.length(), i=0;
    while (i<len) { int cp=Character.codePointAt(t,i); if(!isInvisible(cp)) c++; i+=Character.charCount(cp); }
    return c;
}

boolean isMalicious(CharSequence t) {
    if (t==null||t.length()<50) return false;
    int v=countVisible(t);
    return v==0||(t.length()/v)>MAX_RATIO;
}

boolean hasGlyph(int cp) {
    if (customTf[0]==null) return true;
    if (noGlyphCache.contains(cp)) return false;
    try {
        String s=new String(Character.toChars(cp));
        Paint p1=new Paint(); p1.setTypeface(customTf[0]);
        Paint p2=new Paint(); p2.setTypeface(Typeface.DEFAULT);
        float w1=p1.measureText(s), w2=p2.measureText(s);
        if (w1<=0||(w2>0&&w1<w2*0.3f)) { noGlyphCache.add(cp); return false; }
        return true;
    } catch(Throwable t){ return true; }
}

boolean hasNoGlyph(CharSequence t) {
    if (t==null||t.length()==0) return false;
    int len=t.length(), i=0;
    while (i<len) { if(!hasGlyph(Character.codePointAt(t,i))) return true; i+=Character.charCount(Character.codePointAt(t,i)); }
    return false;
}

void clearGlyphCache() { noGlyphCache.clear(); }
void setCurFont(String p) { currentFontPath=p; }

// ==================== 菜单更新引擎（动态替换 MenuItems Map） ====================
void updateMenuItemText(String oldName, String newName) {
    try {
        Object plugin = PluginManager.INSTANCE.getPlugins().stream()
            .filter(p -> p.getId().equals(pluginId))
            .findFirst().orElse(null);
        if(plugin!=null){
            Map items = plugin.getCompiler().getMenuItems();
            if(items.containsKey(oldName)){
                Object cb = items.remove(oldName);
                items.put(newName, cb);
            } else {
                items.put(newName, "");
            }
        }
    } catch(Throwable t){}
}

// ==================== UI 工具 ====================
int dp(int v) {
    try { return (int)(v*context.getResources().getDisplayMetrics().density+0.5f); }
    catch(Exception e){ return v*2; }
}

GradientDrawable rBg(int color, int r) {
    GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g;
}

void clickAnim(View v) {
    if(v==null) return;
    v.setOnTouchListener((vv,e)->{
        switch(e.getAction()){
            case MotionEvent.ACTION_DOWN: vv.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).setInterpolator(new DecelerateInterpolator()).start(); break;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: vv.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).setInterpolator(new OvershootInterpolator(2f)).start(); break;
        }
        return false;
    });
}

Button mkBtn(Context ctx, String text, int bg, int tc) {
    Button b=new Button(ctx); b.setText(text); b.setTextColor(tc); b.setTextSize(14);
    b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
    b.setBackgroundDrawable(rBg(bg,dp(20))); b.setPadding(dp(16),dp(10),dp(16),dp(10));
    clickAnim(b); return b;
}

String fmtSize(String name, String path) {
    if(path==null||path.isEmpty()) return "📄 系统默认";
    try { long s=new File(path).length(); return name+"  ("+(s>1048576?String.format("%.1fMB",s/1048576.0):String.format("%.0fKB",s/1024.0))+")"; }
    catch(Exception e){ return name; }
}

// ==================== Hook 引擎 ====================
void setupHooks() {
    try {
        Class<?> pCls=classLoader.loadClass("android.graphics.Paint");
        Class<?> tfCls=classLoader.loadClass("android.graphics.Typeface");
        for(Method m:pCls.getDeclaredMethods()){
            if(m.getName().equals("setTypeface")&&m.getParameterTypes().length==1&&m.getParameterTypes()[0]==tfCls){
                m.setAccessible(true);
                unhookSet.add(HookExtensionsKt.hookBefore(m,null,p->{ if(customTf[0]!=null) p.args[0]=customTf[0]; return null; }));
                break;
            }
        }
    } catch(Throwable t){}
    try {
        Class<?> tvCls=classLoader.loadClass("android.widget.TextView");
        for(Method m:tvCls.getDeclaredMethods()){
            if(m.getName().equals("setTypeface")){
                m.setAccessible(true);
                unhookSet.add(HookExtensionsKt.hookBefore(m,null,p->{
                    if(p.args.length>0&&p.args[0] instanceof Typeface&&customTf[0]!=null){
                        try {
                            TextView tv=(TextView)p.thisObject;
                            CharSequence txt=tv.getText();
                            if(txt!=null){
                                if(txt.length()>MAX_LEN) return null;
                                if(isMalicious(txt)){ tv.setVisibility(View.GONE); return null; }
                                if(lagFilter&&hasNoGlyph(txt)) return null;
                            }
                        } catch(Throwable ignored){}
                        p.args[0]=customTf[0];
                    }
                    return null;
                }));
            }
        }
    } catch(Throwable t){}
}

// ==================== 核心功能 ====================
void scanFonts() {
    fontList.clear(); fontPathList.clear();
    fontList.add("系统默认"); fontPathList.add("");
    try {
        File d=new File(pluginPath); if(!d.exists()) d.mkdirs();
        if(d.exists()&&d.isDirectory()){
            File[] fs=d.listFiles();
            if(fs!=null){
                Arrays.sort(fs,(a,b)->a.getName().compareToIgnoreCase(b.getName()));
                for(File f:fs){ String n=f.getName().toLowerCase(); if(f.isFile()&&(n.endsWith(".ttf")||n.endsWith(".otf"))){ fontList.add(f.getName()); fontPathList.add(f.getAbsolutePath()); } }
            }
        }
    } catch(Throwable t){}
}

int findCurIdx() {
    String curCan="", curFn="";
    if(currentFontPath!=null&&!currentFontPath.isEmpty()){ try{ File f=new File(currentFontPath); curCan=f.getCanonicalPath(); curFn=f.getName(); }catch(Exception e){} }
    for(int i=0;i<fontPathList.size();i++){
        String p=fontPathList.get(i);
        if(p.isEmpty()){ if(currentFontPath==null||currentFontPath.isEmpty()) return i; }
        else { try{ File f=new File(p); if(curCan.equals(f.getCanonicalPath())||curFn.equals(f.getName())) return i; }catch(Exception e){} }
    }
    return 0;
}

void applyFont(View v, int depth) {
    if(v==null||depth>30||v.getVisibility()!=View.VISIBLE) return;
    try {
        if(v instanceof TextView){
            TextView tv=(TextView)v;
            if(customTf[0]!=null){
                CharSequence t=tv.getText();
                if(t!=null){ if(t.length()>MAX_LEN) return; if(isMalicious(t)){ tv.setVisibility(View.GONE); return; } }
            }
            Typeface target=customTf[0]!=null?customTf[0]:Typeface.DEFAULT;
            if(tv.getTypeface()!=target) tv.setTypeface(target);
        } else if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) applyFont(g.getChildAt(i),depth+1);
        }
    } catch(Throwable t){}
}

void refreshUI() {
    Object act=getNowActivity();
    if(!(act instanceof Activity)) return;
    ((Activity)act).runOnUiThread(()->{ try{ View root=((Activity)act).getWindow().getDecorView(); if(root!=null) applyFont(root,0); }catch(Throwable t){} });
}

void openFontSite() {
    try { Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.100font.com")); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); toast("请下载 .ttf 格式字体"); }
    catch(Throwable t){ toast("无法打开，请手动访问 100font.com"); }
}

// ==================== 教程弹窗 ====================
void showTutorial() {
    Object a=getNowActivity();
    if(!(a instanceof Activity)) return;
    Activity act=(Activity)a;
    act.runOnUiThread(()->{
        Dialog d=new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root=new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundDrawable(rBg(C_SURFACE,dp(12)));
        root.setPadding(dp(24),dp(16),dp(24),dp(16));

        TextView title=new TextView(act);
        title.setText("📖 导入教程"); title.setTextSize(20);
        title.setTextColor(C_ON_SURFACE); title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        title.setPadding(0,0,0,dp(16)); root.addView(title);

        String[] steps={"① 点击下方「获取字体」跳转 100font.com","② 下载 .ttf 格式的字体文件","③ 将文件放入以下目录：","④ 重新打开列表即可看到新字体"};
        for(String s:steps){ TextView tv=new TextView(act); tv.setText(s); tv.setTextSize(15); tv.setTextColor(C_ON_SURFACE_VAR); tv.setPadding(0,dp(4),0,dp(4)); root.addView(tv); }

        // 路径复制卡片
        TextView pathLabel=new TextView(act);
        pathLabel.setText("📂 插件目录"); pathLabel.setTextSize(13); pathLabel.setTextColor(C_TEXT_SEC);
        pathLabel.setPadding(0,dp(12),0,dp(4)); root.addView(pathLabel);

        LinearLayout pathRow=new LinearLayout(act);
        pathRow.setOrientation(LinearLayout.HORIZONTAL);
        pathRow.setBackgroundDrawable(rBg(Color.parseColor("#F2F3F5"),dp(10)));
        pathRow.setPadding(dp(14),dp(12),dp(14),dp(12));
        pathRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView pathTv=new TextView(act);
        pathTv.setText(pluginPath); pathTv.setTextSize(12); pathTv.setTextColor(C_ON_SURFACE_VAR);
        pathTv.setTypeface(Typeface.MONOSPACE);
        pathTv.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        Button copyBtn=new Button(act);
        copyBtn.setText("复制"); copyBtn.setTextSize(12); copyBtn.setTextColor(C_PRIMARY);
        copyBtn.setBackgroundDrawable(rBg(C_PRIMARY_LIGHT,dp(14)));
        copyBtn.setPadding(dp(12),dp(4),dp(12),dp(4));
        copyBtn.setOnClickListener(v->{ ((ClipboardManager)act.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("path",pluginPath)); toast("✅ 路径已复制"); });

        pathRow.addView(pathTv); pathRow.addView(copyBtn); root.addView(pathRow);

        TextView tip=new TextView(act);
        tip.setText("\n💡 推荐 .ttf 格式，.otf 可能导致图标异常");
        tip.setTextSize(13); tip.setTextColor(C_TEXT_SEC); tip.setPadding(0,dp(8),0,dp(4));
        root.addView(tip);

        Button closeBtn=mkBtn(act,"知道了",C_PRIMARY,C_ON_PRIMARY);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(42)));
        closeBtn.setOnClickListener(v->{ d.dismiss(); });
        root.addView(closeBtn);

        ScrollView sv=new ScrollView(act); sv.addView(root); d.setContentView(sv);
        Window w=d.getWindow();
        if(w!=null){ w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); w.setLayout((int)(act.getResources().getDisplayMetrics().widthPixels*0.88),ViewGroup.LayoutParams.WRAP_CONTENT); }
        d.show();
});
}

// ==================== 主界面（美化版） ====================
void showFontDialog() {
    Object a=getNowActivity();
    if(!(a instanceof Activity)){ toast("无法获取界面"); return; }
    Activity act=(Activity)a;
    act.runOnUiThread(()->{
        scanFonts();
        int curIdx=findCurIdx();

        Dialog d=new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root=new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundDrawable(rBg(C_SURFACE,dp(12)));
        root.setPadding(dp(24),dp(20),dp(24),dp(20));

        // ===== 标题栏 =====
        LinearLayout hdr=new LinearLayout(act);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(0,0,0,dp(4));

        TextView titleTxt=new TextView(act);
        titleTxt.setText("🔤 字体替换"); titleTxt.setTextSize(20);
        titleTxt.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        titleTxt.setTextColor(C_ON_SURFACE);

        TextView curBadge=new TextView(act);
        curBadge.setText("当前: "+fontList.get(curIdx));
        curBadge.setTextSize(12); curBadge.setTextColor(C_PRIMARY);
        curBadge.setPadding(dp(10),dp(3),dp(10),dp(3));
        curBadge.setBackgroundDrawable(rBg(C_PRIMARY_LIGHT,dp(10)));
        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin=dp(8);

        hdr.addView(titleTxt); hdr.addView(curBadge,blp); root.addView(hdr);

        // 分隔线
        View div=new View(act);
        div.setBackgroundColor(C_OUTLINE);
        div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1));
        div.setPadding(0,dp(12),0,dp(12));
        root.addView(div);

        // ===== 字体列表（卡片式） =====
        ScrollView sv=new ScrollView(act);
        sv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(320)));

        LinearLayout listLy=new LinearLayout(act);
        listLy.setOrientation(LinearLayout.VERTICAL);

        final int[] selIdx={curIdx};
        final View[][] cards=new View[fontList.size()][1];

        for(int i=0;i<fontList.size();i++){
            final int fi=i;
            String label=fmtSize(fontList.get(i),fontPathList.get(i));

            LinearLayout card=new LinearLayout(act);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14),dp(12),dp(14),dp(12));
            card.setClickable(true);
            card.setBackgroundDrawable(rBg(i==selIdx[0]?C_SELECTED:Color.TRANSPARENT,dp(10)));

            // 圆点
            View dot=new View(act);
            int ds=dp(8);
            LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(ds,ds);
            dlp.rightMargin=dp(10);
            dot.setLayoutParams(dlp);
            GradientDrawable dotBg=new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(i==selIdx[0]?C_PRIMARY:C_OUTLINE);
            dot.setBackgroundDrawable(dotBg);

            // 名称
            TextView ntv=new TextView(act);
            ntv.setText(label); ntv.setTextSize(15); ntv.setTextColor(C_ON_SURFACE);
            ntv.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

            // ✓
            TextView ck=new TextView(act);
            ck.setText(i==selIdx[0]?"✓":"");
            ck.setTextSize(18); ck.setTextColor(C_PRIMARY);
            ck.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));

            card.addView(dot); card.addView(ntv); card.addView(ck);
            listLy.addView(card);
            cards[i][0]=card;

            // 分隔线
            if(i<fontList.size()-1){
                View dv=new View(act);
                dv.setBackgroundColor(Color.parseColor("#F0F0F0"));
                dv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1));
                listLy.addView(dv);
            }

            card.setOnClickListener(v->{
                int old=selIdx[0];
                if(old==fi) return;
                selIdx[0]=fi;

                // 实时显示当前选中
                curBadge.setText("当前: "+fontList.get(fi));

                // 取消旧选中
                if(old>=0&&old<cards.length&&cards[old][0]!=null){
                    LinearLayout oc=(LinearLayout)cards[old][0];
                    oc.setBackgroundDrawable(rBg(Color.TRANSPARENT,dp(10)));
                    View od=oc.getChildAt(0);
                    if(od!=null){ GradientDrawable db=new GradientDrawable(); db.setShape(GradientDrawable.OVAL); db.setColor(C_OUTLINE); od.setBackgroundDrawable(db); }
                    View ock=oc.getChildAt(2);
                    if(ock instanceof TextView) ((TextView)ock).setText("");
                }
                // 更新新选中
                if(cards[fi][0]!=null){
                    LinearLayout nc=(LinearLayout)cards[fi][0];
                    nc.setBackgroundDrawable(rBg(C_SELECTED,dp(10)));
                    View nd=nc.getChildAt(0);
                    if(nd!=null){ GradientDrawable db=new GradientDrawable(); db.setShape(GradientDrawable.OVAL); db.setColor(C_PRIMARY); nd.setBackgroundDrawable(db); }
                    View nck=nc.getChildAt(2);
                    if(nck instanceof TextView) ((TextView)nck).setText("✓");
                }
                // 更新预览
                updatePreview(act,previewLy,fontPathList.get(fi));
            });
        }

        sv.addView(listLy);
        root.addView(sv);

        // ===== 预览区 =====
        final LinearLayout previewLy=new LinearLayout(act);
        previewLy.setOrientation(LinearLayout.VERTICAL);
        previewLy.setBackgroundDrawable(rBg(C_SURFACE_VAR,dp(10)));
        previewLy.setPadding(dp(14),dp(12),dp(14),dp(12));

        TextView pvLabel=new TextView(act);
        pvLabel.setText("📝 预览"); pvLabel.setTextSize(12); pvLabel.setTextColor(C_TEXT_SEC);
        previewLy.addView(pvLabel);

        final TextView pvText=new TextView(act);
        pvText.setText("窗前明月光，疑是地上霜。\nThe quick brown fox jumps over the lazy dog.\n0123456789 ABC abc !@#$%");
        pvText.setTextSize(15); pvText.setTextColor(C_ON_SURFACE);
        pvText.setLineSpacing(dp(4),1.0f); pvText.setPadding(0,dp(6),0,0);
        // 应用当前选中的字体到预览
        String selPath=fontPathList.get(selIdx[0]);
        if(selPath!=null&&!selPath.isEmpty()){ try{ pvText.setTypeface(Typeface.createFromFile(new File(selPath))); }catch(Throwable t){} }
        previewLy.addView(pvText);
        root.addView(previewLy);

        // ===== 卡顿字符屏蔽开关 =====
        LinearLayout lagRow=new LinearLayout(act);
        lagRow.setOrientation(LinearLayout.HORIZONTAL);
        lagRow.setGravity(Gravity.CENTER_VERTICAL);
        lagRow.setPadding(dp(14),dp(12),dp(14),0);

        LinearLayout lagTxtLy=new LinearLayout(act);
        lagTxtLy.setOrientation(LinearLayout.VERTICAL);
        lagTxtLy.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        TextView lagLbl=new TextView(act);
        lagLbl.setText("屏蔽卡顿字符"); lagLbl.setTextSize(15); lagLbl.setTextColor(C_ON_SURFACE);
        TextView lagDesc=new TextView(act);
        lagDesc.setText("字体缺字时自动回退系统字体"); lagDesc.setTextSize(12); lagDesc.setTextColor(C_TEXT_SEC);
        lagTxtLy.addView(lagLbl); lagTxtLy.addView(lagDesc);

        Switch lagSw=new Switch(act);
        lagSw.setChecked(lagFilter);
        lagSw.setScaleX(2.0f);
        lagSw.setScaleY(2.0f);
        lagSw.setOnCheckedChangeListener((bv,isChecked)->{
            lagFilter=isChecked;
            putString("font_config","lag_filter",isChecked?"1":"0");
            if(!isChecked) clearGlyphCache();
            toast(isChecked?"卡顿字符屏蔽已开启":"卡顿字符屏蔽已关闭");
        });

        lagRow.addView(lagTxtLy); lagRow.addView(lagSw);
        root.addView(lagRow);

        // ===== 按钮栏 =====
        LinearLayout btnRow=new LinearLayout(act);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0,dp(16),0,0);

        Button btnTut=mkBtn(act,"📖 教程",C_SURFACE_VAR,C_ON_SURFACE_VAR);
        Button btnFont=mkBtn(act,"🔍 获取字体",C_SURFACE_VAR,C_ON_SURFACE_VAR);
        Button btnOk=mkBtn(act,"✓ 应用",C_PRIMARY,C_ON_PRIMARY);

        LinearLayout.LayoutParams blp2=new LinearLayout.LayoutParams(0,dp(42),1);
        blp2.leftMargin=dp(4); blp2.rightMargin=dp(4);
        btnTut.setLayoutParams(blp2); btnFont.setLayoutParams(blp2); btnOk.setLayoutParams(blp2);

        btnTut.setOnClickListener(v->{ d.dismiss(); showTutorial(); });
        btnFont.setOnClickListener(v->{ d.dismiss(); openFontSite(); });
        btnOk.setOnClickListener(v->{
            int idx=selIdx[0];
            if(idx>=0&&idx<fontPathList.size()){
                String path=fontPathList.get(idx);
                String name=fontList.get(idx);
                d.dismiss();
                toast("⏳ 正在切换字体...");

                if(path.isEmpty()){
                    customTf[0]=null; setCurFont(null);
                    putString("font_config","current_font","");
                } else {
                    try{
                        customTf[0]=Typeface.createFromFile(new File(path));
                        setCurFont(path);
                        putString("font_config","current_font",path);
                    } catch(Throwable t){ toast("⚠️ 字体文件损坏或无法加载"); return; }
                }
                clearGlyphCache();
                h.postDelayed(()->{
                    refreshUI();
                    // 动态更新菜单项名称
                    String newLabel = path.isEmpty() ? "🔤 字体替换" : ("🔤 字体 ["+name+"]");
                    updateMenuItemText(currentMenuLabel, newLabel);
                    currentMenuLabel = newLabel;
                    toast("✅ 已切换: "+name);
                },500);
            }
        });

        btnRow.addView(btnTut); btnRow.addView(btnFont); btnRow.addView(btnOk);
        root.addView(btnRow);

        d.setContentView(root);
        d.setCancelable(true);

        Window w=d.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int)(act.getResources().getDisplayMetrics().widthPixels*0.92),ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.5f);
        }
        d.show();
    });
}

void updatePreview(Activity act, LinearLayout ly, String path) {
    if(ly==null||ly.getChildCount()<2) return;
    View c=ly.getChildAt(1);
    if(!(c instanceof TextView)) return;
    TextView tv=(TextView)c;
    if(path==null||path.isEmpty()) tv.setTypeface(Typeface.DEFAULT);
    else { try{ tv.setTypeface(Typeface.createFromFile(new File(path))); }catch(Throwable t){ tv.setTypeface(Typeface.DEFAULT); } }
}

// ==================== 入口 ====================
void onFontMenuClick(int chatType, String peerUin, String name, Object contact) {
    showFontDialog();
}

// ===== 启动初始化 =====
String savedFont=getString("font_config","current_font","");
if(savedFont!=null) savedFont=savedFont.trim();
if(savedFont!=null&&!savedFont.isEmpty()){
    try{ File f=new File(savedFont); if(f.exists()){ customTf[0]=Typeface.createFromFile(f); setCurFont(savedFont); } }catch(Throwable t){}
}

String lagSaved=getString("font_config","lag_filter","1");
lagFilter="1".equals(lagSaved);

// ===== 菜单项 =====
String startLabel = "🔤 字体替换";
if(currentFontPath!=null&&!currentFontPath.isEmpty()){
    try{ String fn=new File(currentFontPath).getName(); startLabel="🔤 字体 ["+fn+"]"; }catch(Exception e){}
}
currentMenuLabel = startLabel;
addItem(currentMenuLabel,"onFontMenuClick");

setupHooks();

public void unLoadPlugin() {
    synchronized(unhookSet){
        for(Object u:unhookSet){ try{ u.getClass().getMethod("unhook").invoke(u); }catch(Throwable t){} }
        unhookSet.clear();
    }
}