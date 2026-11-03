package org.clashledger.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;

public final class MainActivity extends Activity {
    private final int gold = Color.rgb(242,196,108), ink = Color.rgb(242,245,247), muted = Color.rgb(144,158,174);
    private final int canvas = Color.rgb(12,18,27), surface = Color.rgb(22,31,44), line = Color.rgb(41,54,72), green = Color.rgb(143,219,166);
    private Ledger ledger;
    private LinearLayout content, timerList, tabs;
    private TextView boostStatus, boostCaption, overview, nextBuilder, readyCount, reminderStatusView;
    private Typeface displayFace;
    private boolean builderBase;
    private final java.util.List<TimerBinding> bindings = new java.util.ArrayList<>();
    private static final class TimerBinding {
        final Upgrade upgrade; final TextView remaining, finish; final ProgressBar progress;
        TimerBinding(Upgrade u, TextView r, TextView f, ProgressBar p) { upgrade=u; remaining=r; finish=f; progress=p; }
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { public void run() { updateTimers(); handler.postDelayed(this, 1000); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        displayFace = Typeface.createFromAsset(getAssets(), "fonts/BarlowCondensed-Bold.ttf");
        if (state != null) builderBase = state.getBoolean("builderBase");
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setStatusBarColor(canvas); getWindow().setNavigationBarColor(canvas);
        ledger = new Ledger(this);
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("builderBase", builderBase); super.onSaveInstanceState(state); }
    @Override protected void onResume() { super.onResume(); ledger = new Ledger(this); build(); handler.removeCallbacks(tick); handler.post(tick); UpgradeWidget.refresh(this); Reminders.sync(this,true); updateReminderStatus(); consumeSharedExport(); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    private void consumeSharedExport() {
        android.content.Intent i=getIntent();
        if(!android.content.Intent.ACTION_SEND.equals(i.getAction()))return;
        setIntent(new android.content.Intent());
        CharSequence text=i.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT);
        if(text!=null) { if(text.length()>1_000_000)Toast.makeText(this,"Export exceeds 1 MB",Toast.LENGTH_LONG).show();else importJson(text.toString()); }
        else { android.net.Uri uri=i.getParcelableExtra(android.content.Intent.EXTRA_STREAM);if(uri!=null)readExport(uri); }
    }
    @Override protected void onNewIntent(android.content.Intent i) { super.onNewIntent(i);setIntent(i); }
    @Override protected void onActivityResult(int request,int result,android.content.Intent data) {
        super.onActivityResult(request,result,data);
        if(request==700&&result==RESULT_OK&&data!=null&&data.getData()!=null)readExport(data.getData());
    }
    private void readExport(android.net.Uri uri) {
        new Thread(()->{
            try(java.io.InputStream input=getContentResolver().openInputStream(uri)) {
                if(input==null)throw new java.io.IOException("Cannot open file");
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1) { if(bytes.size()+n>1_000_000)throw new java.io.IOException("Export exceeds 1 MB");bytes.write(buffer,0,n); }
                String value=bytes.toString("UTF-8");runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())importJson(value);});
            }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Could not read export: "+e.getMessage(),Toast.LENGTH_LONG).show());}
        },"export-reader").start();
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int color, int radius) { GradientDrawable b = new GradientDrawable(); b.setColor(color); b.setCornerRadius(dp(radius)); return b; }
    private GradientDrawable panel(int first, int second, int border, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{first, second});
        d.setCornerRadius(dp(radius)); d.setStroke(dp(1),border); return d;
    }
    private void touch(View view, android.graphics.drawable.Drawable background, Runnable action) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x25FFFFFF), background, null));
        view.setOnClickListener(v -> action.run()); view.setFocusable(true);
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("tnum"); t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }
    private TextView title(String value, int size) { TextView t=text(value,size,ink,true); t.setTypeface(displayFace); return t; }
    private TextView label(String value, int color) { TextView t=text(value,10,color,true); t.setLetterSpacing(0.13f); return t; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l=new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height)); }
    private void space(LinearLayout parent,int height) { parent.addView(new View(this),lp(1,height)); }
    private ImageView art(int resource, int padding) { ImageView i=new ImageView(this); i.setImageResource(resource); i.setScaleType(ImageView.ScaleType.FIT_CENTER); i.setPadding(dp(padding),dp(padding),dp(padding),dp(padding)); i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return i; }
    private ImageView iconButton(int resource,String description,Runnable action) {
        ImageView image=art(resource,13); image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); image.setContentDescription(description);
        touch(image,panel(surface,surface,line,14),action); return image;
    }
    private Button button(String value, Runnable action) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextColor(gold); b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); b.setMinHeight(dp(46)); b.setMinimumHeight(dp(46)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(14),dp(9),dp(14),dp(9)); b.setStateListAnimator(null);
        touch(b,panel(0xFF243044,0xFF1B2637,line,12),action); return b;
    }
    private Button primary(String value, Runnable action) {
        Button b=button(value,action); b.setTextColor(0xFF302415);
        touch(b,panel(0xFFF7D88F,0xFFE7B65A,0xFFFFD990,12),action); return b;
    }
    private void build() {
        LinearLayout root=column(); root.setBackgroundColor(canvas);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        content=column(); content.setPadding(dp(20),dp(14),dp(20),dp(24)); scroll.addView(content); root.addView(scroll,lp(-1,-1));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();

        LinearLayout brand=horizontal();
        ImageView mark=art(R.drawable.art_28000001,2); mark.setBackground(panel(0xFF423925,0xFF242A2B,0xFF665438,12)); brand.addView(mark,lp(38,38));
        LinearLayout wordmark=column(); wordmark.setPadding(dp(10),0,0,0); wordmark.addView(title("CLASH LEDGER",22));
        wordmark.addView(label("YOUR VILLAGE COMPANION",muted)); brand.addView(wordmark,new LinearLayout.LayoutParams(0,-2,1));
        brand.addView(iconButton(R.drawable.ic_widget,"Add home screen widget",this::pinWidget),lp(44,44));
        LinearLayout.LayoutParams settingsParams=lp(44,44); settingsParams.leftMargin=dp(8);
        brand.addView(iconButton(R.drawable.ic_settings,"Settings",this::settingsMenu),settingsParams); content.addView(brand);
        space(content,17);
        LinearLayout heading=horizontal(); heading.addView(label("VILLAGE OVERVIEW",muted),new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(label("●  OFFLINE",green)); content.addView(heading); space(content,12);

        LinearLayout hero=column(); hero.setPadding(dp(16),dp(12),dp(12),dp(12));
        hero.setBackground(panel(0xFF293649,0xFF182B2B,0xFF40534E,22));
        LinearLayout heroTop=horizontal(); LinearLayout heroCopy=column();
        heroCopy.addView(label("HOME VILLAGE",gold)); space(heroCopy,7);
        TextView village=title(ledger.townHall>0 ? "Town Hall "+ledger.townHall : "Your village",29); heroCopy.addView(village);
        space(heroCopy,5); TextView player=text(ledger.village,12,0xFFCDD6DC,true); player.setMaxLines(1); player.setEllipsize(android.text.TextUtils.TruncateAt.END); heroCopy.addView(player);
        space(heroCopy,10); TextView local=text(ledger.importedAt>0 ? "Export · " + DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(ledger.importedAt)) : "Import your village to get started",10,0xFF9DAFB8,false); heroCopy.addView(local);
        heroTop.addView(heroCopy,new LinearLayout.LayoutParams(0,-2,1));
        ImageView town=art(Artwork.townHall(this,ledger.townHall),0); heroTop.addView(town,lp(100,102)); hero.addView(heroTop);
        space(hero,9);
        Button importer=primary("Import village JSON",this::importJson); importer.setCompoundDrawablePadding(dp(8));
        android.graphics.drawable.Drawable download=getDrawable(R.drawable.ic_import); download.setTint(0xFF49351A); download.setBounds(0,0,dp(18),dp(18)); importer.setCompoundDrawables(download,null,null,null);
        hero.addView(importer,lp(-1,42)); content.addView(hero);
        space(content,12);

        LinearLayout metrics=horizontal();
        overview=metric(metrics,"BUILDERS",R.drawable.ic_hammer,gold);
        nextBuilder=metric(metrics,"NEXT FREE",R.drawable.ic_clock,green);
        readyCount=metric(metrics,"READY",R.drawable.ic_shield,0xFFADA7FF);
        content.addView(metrics); space(content,16);

        LinearLayout boost=horizontal(); boost.setPadding(dp(10),dp(9),dp(12),dp(9)); boost.setBackground(panel(0xFF302B47,0xFF242238,0xFF524563,18));
        boost.addView(art(R.drawable.art_builder_potion,0),lp(58,70));
        LinearLayout potionCopy=column(); potionCopy.setPadding(dp(10),0,dp(6),0); potionCopy.addView(label("POTIONS & HELPERS",0xFFCEB3F4)); space(potionCopy,5);
        potionCopy.addView(title("Your village, boosted",22)); space(potionCopy,5);
        boostStatus=text("",12,0xFFD6C4ED,true); potionCopy.addView(boostStatus);
        boostCaption=text("",10,muted,false); boostCaption.setVisibility(View.VISIBLE); potionCopy.addView(boostCaption);
        boost.addView(potionCopy,new LinearLayout.LayoutParams(0,-2,1));
        boost.addView(iconButton(R.drawable.ic_plus,"Boosts and helpers",this::boostOptions),lp(44,44));
        boost.setOnClickListener(v -> boostOptions()); content.addView(boost);
        space(content,16);

        tabs=horizontal(); tabs.setPadding(dp(4),dp(4),dp(4),dp(4)); tabs.setBackground(bg(0xFF161F2C,14));
        content.addView(tabs,lp(-1,48)); renderTabs(); space(content,12);
        LinearLayout upgradeHeading=horizontal(); upgradeHeading.addView(title("Upgrade queue",26),new LinearLayout.LayoutParams(0,-2,1));
        Button add=button("+ Add",()->editor(null)); upgradeHeading.addView(add,lp(76,44)); content.addView(upgradeHeading); space(content,10);
        timerList=column(); content.addView(timerList); renderTimers();
        space(content,16);
        LinearLayout widgetCard=horizontal(); widgetCard.setPadding(dp(16),dp(16),dp(14),dp(16));
        touch(widgetCard,panel(0xFF1F2B39,0xFF17232E,line,18),this::pinWidget);
        widgetCard.addView(art(R.drawable.ic_widget,3),lp(30,30));
        LinearLayout widgetCopy=column(); widgetCopy.setPadding(dp(12),0,dp(8),0); widgetCopy.addView(title("Take your village home",21)); space(widgetCopy,4);
        widgetCopy.addView(text("Add the live home screen widget",12,muted,false)); widgetCard.addView(widgetCopy,new LinearLayout.LayoutParams(0,-2,1)); widgetCard.addView(art(R.drawable.ic_arrow,0),lp(20,20)); content.addView(widgetCard);
        space(content,22);
        TextView credits=text("Built for Clashers. All your data stays on this device.\nUnofficial fan content · Artwork © Supercell",11,muted,false); credits.setGravity(Gravity.CENTER); credits.setLineSpacing(dp(5),1); content.addView(credits);
        Button about=button("About & artwork credits",this::about); LinearLayout.LayoutParams aboutParams=lp(-1,44); aboutParams.topMargin=dp(8); content.addView(about,aboutParams);
        updateTimers();
    }
    private TextView metric(LinearLayout parent,String label,int icon,int color) {
        LinearLayout cell=column(); cell.setPadding(dp(11),dp(12),dp(8),dp(12)); cell.setBackground(panel(surface,0xFF17212F,line,14));
        LinearLayout top=horizontal(); ImageView image=art(icon,0); image.setColorFilter(color); top.addView(image,lp(12,12));
        TextView name=label(label,muted); name.setTextSize(9); name.setLetterSpacing(0.04f); name.setPadding(dp(5),0,0,0); top.addView(name); cell.addView(top); space(cell,7);
        TextView value=title("—",25); value.setTextColor(color); value.setMaxLines(1); cell.addView(value);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1); if(parent.getChildCount()>0)p.leftMargin=dp(8); parent.addView(cell,p); return value;
    }
    private void renderTabs() {
        tabs.removeAllViews();
        String[] labels={"Home Village","Builder Base"};
        for(int i=0;i<labels.length;i++) {
            final boolean base=i==1; boolean selected=builderBase==base;
            TextView tab=text(labels[i],13,selected?gold:muted,true); tab.setGravity(Gravity.CENTER);
            touch(tab,selected?panel(0xFF30384A,0xFF283143,0xFF485062,11):bg(Color.TRANSPARENT,11),()->{builderBase=base;renderTabs();renderTimers();updateTimers();});
            tab.setSelected(selected); tabs.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
        }
    }
    private String clock(long ms) {
        long s=Math.max(0,ms/1000); return String.format(java.util.Locale.getDefault(),"%02d:%02d",s/60,s%60);
    }
    private void updateTimers() {
        if (timerList == null) return;
        long now=System.currentTimeMillis(), soonest=Long.MAX_VALUE; int ready=0;
        for(Upgrade u:ledger.upgrades) {
            long left=u.wallRemaining(now,ledger.boostUntil);
            if(left<=0) ready++; else if(u.isBuilder()) soonest=Math.min(soonest,left);
        }
        overview.setText(ledger.busy(now)+" / "+ledger.builders+(ledger.goblins(now)>0?" + G":""));
        nextBuilder.setText(soonest==Long.MAX_VALUE?"Available":Upgrade.duration(soonest));
        readyCount.setText(String.format(java.util.Locale.getDefault(),"%02d",ready));
        boolean boosted=ledger.boostUntil>now;
        boostStatus.setText(ledger.boosts.summary(now,ledger.boostUntil));
        String helper="";for(Upgrade u:ledger.upgrades)if(u.remaining(now,ledger.boostUntil)>0&&!u.helperLabel(now).isEmpty())helper+=(helper.isEmpty()?"":"\n")+u.name+" · "+u.helperLabel(now);
        boostCaption.setText(helper.isEmpty()?"Tap to manage potions, helpers & events":helper);
        for(TimerBinding b:bindings) {
            Upgrade u=b.upgrade; long left=u.wallRemaining(now,ledger.boostUntil);
            b.remaining.setText(left>0?TimerFormat.format(left,timerStyle()):"Ready to collect");
            b.remaining.setTextColor(left>0?ink:green);
            b.finish.setText(left>0?"↗ "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(now+left))+(u.helperLabel(now).isEmpty()?"":"  ·  "+u.helperLabel(now)):"Upgrade complete");
            b.progress.setProgress(u.progress(now,ledger.boostUntil));
        }
    }
    private int groupColor(String group) { return group.equals("Laboratory")||group.equals("Star Laboratory")?0xFFB8A1ED:group.equals("Pets")?0xFF81C9DE:gold; }
    private void renderTimers() {
        bindings.clear(); timerList.removeAllViews();
        long now=System.currentTimeMillis();
        boolean hasAny=false;
        for(String group:Upgrade.GROUPS) {
            boolean base=group.equals("Builder Base")||group.equals("Star Laboratory"); if(base!=builderBase)continue;
            java.util.List<Upgrade> list=new java.util.ArrayList<>();
            for(Upgrade u:ledger.upgrades)if(u.group.equals(group))list.add(u);
            list.sort(java.util.Comparator.comparingLong(u->u.wallRemaining(now,ledger.boostUntil)));
            if(list.isEmpty())continue; hasAny=true;
            LinearLayout section=horizontal(); TextView sectionName=label(group.toUpperCase(java.util.Locale.ROOT),groupColor(group)); section.addView(sectionName,new LinearLayout.LayoutParams(0,-2,1));
            TextView count=text(String.format(java.util.Locale.getDefault(),"%02d",list.size()),11,muted,true); section.addView(count); section.setPadding(0,dp(9),dp(2),dp(10)); timerList.addView(section);
            for(Upgrade u:list) {
                LinearLayout row=column(); row.setPadding(dp(12),dp(12),dp(12),dp(12));
                touch(row,panel(0xFF1C2737,0xFF17212F,line,17),()->editor(u));
                LinearLayout upper=horizontal();
                FrameLayout portrait=new FrameLayout(this); portrait.setBackground(panel(0xFF344053,0xFF242E3F,0xFF40506A,13));
                portrait.addView(art(Artwork.forName(this,u.name),4),new FrameLayout.LayoutParams(-1,-1)); upper.addView(portrait,lp(60,66));
                LinearLayout copy=column(); copy.setPadding(dp(12),0,0,0);
                LinearLayout nameRow=horizontal(); TextView name=text(u.name,15,ink,true); name.setMaxLines(2); nameRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                nameRow.addView(art(R.drawable.ic_arrow,2),lp(16,16)); copy.addView(nameRow); space(copy,5);
                TextView level=label("LEVEL "+Math.max(0,u.level-1)+"  →  "+u.level+(u.goblin?"   • GOBLIN":""),u.goblin?green:muted); level.setTextSize(9); level.setLetterSpacing(0.04f); copy.addView(level); space(copy,7);
                TextView remaining=text("",19,ink,true); copy.addView(remaining); upper.addView(copy,new LinearLayout.LayoutParams(0,-2,1)); row.addView(upper); space(row,10);
                ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setProgressDrawable(getDrawable(R.drawable.progress)); bar.setProgressTintList(ColorStateList.valueOf(groupColor(group))); bar.setMax(1000); row.addView(bar,lp(-1,4));
                space(row,7); TextView finish=text("",10,muted,false); row.addView(finish); bindings.add(new TimerBinding(u,remaining,finish,bar));
                LinearLayout.LayoutParams p=lp(-1,-2); p.bottomMargin=dp(9); timerList.addView(row,p);
                row.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Remove "+u.name+"?").setMessage("This removes only the local timer.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{ledger.upgrades.remove(u);changed();}).show();return true;});
            }
        }
        if(!hasAny) {
            LinearLayout empty=column(); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24),dp(22),dp(24),dp(24)); empty.setBackground(panel(surface,0xFF131E2C,line,18));
            empty.addView(art(Artwork.forName(this,"Barbarian King"),0),lp(82,82)); space(empty,13); empty.addView(title("A new chapter awaits",24)); space(empty,7);
            TextView hint=text("Import your village or add an upgrade.\nWe'll keep an eye on the countdown.",13,muted,false); hint.setGravity(Gravity.CENTER); hint.setLineSpacing(dp(4),1); empty.addView(hint); timerList.addView(empty);
        }
    }
    private String timerStyle() { return getSharedPreferences("display",MODE_PRIVATE).getString("timerStyle","compact"); }
    private void displayOptions() {
        String[] keys={"compact","words","clock"};
        String[] names={"Days / hours / minutes","Friendly wording","Clock · always shows seconds"};
        LinearLayout f=form();
        f.addView(text("One countdown per upgrade. Choose Clock for a second-by-second countdown in the app and widget.",13,muted,false));space(f,12);
        RadioGroup choices=new RadioGroup(this);choices.setOrientation(RadioGroup.VERTICAL);f.addView(choices);
        RadioButton[] buttons=new RadioButton[keys.length];long example=(24*3600L+3*3600+5*60+12)*1000;
        for(int i=0;i<keys.length;i++) {
            RadioButton option=new RadioButton(this);option.setId(View.generateViewId());option.setTextColor(ink);option.setTextSize(14);option.setPadding(dp(4),dp(10),dp(8),dp(10));option.setMinHeight(dp(68));
            option.setText(names[i]+"\n"+TimerFormat.format(example,keys[i]));buttons[i]=option;choices.addView(option,lp(-1,-2));
            if(keys[i].equals(timerStyle()))choices.check(option.getId());
        }
        choices.setOnCheckedChangeListener((group,id)->{
            for(int i=0;i<buttons.length;i++)if(buttons[i].getId()==id) {
                getSharedPreferences("display",MODE_PRIVATE).edit().putString("timerStyle",keys[i]).remove("showSeconds").apply();
                UpgradeWidget.refresh(this);updateTimers();break;
            }
        });
        ScrollView scroll=new ScrollView(this);scroll.addView(f);
        new AlertDialog.Builder(this).setTitle("Countdown format").setView(scroll).setPositiveButton("Done",null).show();
    }
    private void boostOptions() {
        AlertDialog menu=new AlertDialog.Builder(this).setTitle("Boosts & helpers").setPositiveButton("Done",null).create();
        LinearLayout f=form(); long now=System.currentTimeMillis();
        f.addView(text(ledger.boosts.summary(now,ledger.boostUntil),14,green,true)); space(f,12);
        f.addView(text("Record changes after using them in-game. Imports replace boost windows and reset manual event adjustments.",12,muted,false));
        f.addView(button("Builder Potion · 10×",()->{menu.dismiss();boostEditor(-1);}));
        for(int i=0;i<BoostState.NAMES.length;i++) { final int index=i; space(f,5); f.addView(button(BoostState.NAMES[i]+" · "+BoostState.RATES[i]+"×",()->{menu.dismiss();boostEditor(index);})); }
        space(f,12); f.addView(button("Builder’s Apprentice / Lab Assistant",()->{menu.dismiss();helperOptions();}));
        space(f,5); f.addView(button("Event multiplier adjustment",()->{menu.dismiss();eventOptions();}));
        ScrollView scroll=new ScrollView(this);scroll.addView(f);
        menu.setView(scroll);menu.show();
    }
    private void boostEditor(int index) {
        String name=index<0?"Builder Potion":BoostState.NAMES[index]; long now=System.currentTimeMillis();
        long end=index<0?ledger.boostUntil:ledger.boosts.ends[index];
        LinearLayout f=form(); f.addView(text("Set remaining time, or record a use in-game. Builder Potion overrides Builder Bite while both run. Helpers contribute separately.",12,muted,false));
        EditText minutes=field(f,"Minutes remaining · 0 ends boost",String.valueOf(Math.max(0,(end-now+59999)/60000)),true);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(name).setView(f).setNegativeButton("Cancel",null)
            .setNeutralButton(index==3?"Record +30 min":"Record +1 hour",(x,w)->{long t=System.currentTimeMillis();ledger.settle(t);long old=index<0?ledger.boostUntil:ledger.boosts.ends[index];long next=Math.max(t,old)+(index==3?1800000L:3600000L);if(index<0)ledger.boostUntil=next;else ledger.boosts.ends[index]=next;changed();})
            .setPositiveButton("Save",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try { int n=Integer.parseInt(minutes.getText().toString());if(n<0||n>10080)throw new IllegalArgumentException();long t=System.currentTimeMillis();ledger.settle(t);long next=n==0?0:t+n*60000L;if(index<0)ledger.boostUntil=next;else ledger.boosts.ends[index]=next;changed();d.dismiss(); }catch(IllegalArgumentException e){minutes.setError("Use 0–10080 minutes");}}));d.show();
    }
    private void eventOptions() {
        LinearLayout f=form();f.addView(text("Additional speed from NOW, for one category. Leave 1× if the exported timer already includes Hammer Jam, Gold Pass or another duration discount. This is a manual estimate, not an event calendar. Helpers are not multiplied.",13,muted,false));
        Spinner group=new Spinner(this);group.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,Upgrade.GROUPS));
        for(int i=0;i<Upgrade.GROUPS.length;i++)if(Upgrade.GROUPS[i].equals(ledger.boosts.eventGroup))group.setSelection(i);f.addView(group);
        EditText rate=field(f,"Speed multiplier · 1 disables",String.valueOf(ledger.boosts.eventRate),true);rate.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText minutes=field(f,"Minutes remaining",String.valueOf(Math.max(0,(ledger.boosts.eventEnd-System.currentTimeMillis()+59999)/60000)),true);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Event adjustment").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{double r=Double.parseDouble(rate.getText().toString());int m=Integer.parseInt(minutes.getText().toString());if(!Double.isFinite(r)||r<1||r>10||m<0||m>527040)throw new IllegalArgumentException();long t=System.currentTimeMillis();ledger.settle(t);ledger.boosts.eventStart=t;ledger.boosts.eventEnd=t+m*60000L;ledger.boosts.eventRate=r;ledger.boosts.eventGroup=Upgrade.GROUPS[group.getSelectedItemPosition()];changed();d.dismiss();}catch(IllegalArgumentException e){rate.setError("Multiplier 1–10; duration 0–527040 minutes");}}));d.show();
    }
    private void helperOptions() {
        AlertDialog menu=new AlertDialog.Builder(this).setTitle("Helpers & repeat forecasts").setPositiveButton("Done",null).create();
        LinearLayout f=form();long now=System.currentTimeMillis();
        f.addView(text("Apprentice L"+ledger.boosts.apprenticeLevel+" · "+(ledger.boosts.apprenticeReady>now?"available in "+Upgrade.duration(ledger.boosts.apprenticeReady-now):"available")+"\nLab Assistant L"+ledger.boosts.labLevel,14,ink,true));
        f.addView(text("Imported sessions use the helper’s level and active time remaining. Future repeat sessions are forecasts: 1 hour every 23 hours on the assigned upgrade. Re-import if the assignment changes. Goblin jobs cannot receive a manual helper assignment.",12,muted,false));
        CheckBox repeat=new CheckBox(this);repeat.setText("Include future repeat sessions in estimates");repeat.setTextColor(ink);repeat.setChecked(ledger.boosts.repeatHelpers);f.addView(repeat);
        repeat.setOnCheckedChangeListener((b,value)->{ledger.settle(System.currentTimeMillis());ledger.boosts.repeatHelpers=value;changed();});
        for(Upgrade u:ledger.upgrades)if((u.isBuilder()||u.group.equals("Laboratory"))&&!u.goblin&&u.remaining(now,ledger.boostUntil)>0) {
            space(f,5);f.addView(button(u.name+(u.helperLevel>0?" · helper L"+u.helperLevel:" · assign"),()->{menu.dismiss();helperEditor(u);}));
        }
        ScrollView sc=new ScrollView(this);sc.addView(f);menu.setView(sc);menu.show();
    }
    private void helperEditor(Upgrade u) {
        LinearLayout f=form();f.addView(text("Assign only after starting the helper in-game. This moves the tracked helper from any other job in the category. Level adds that many work hours per active hour.",12,muted,false));
        EditText level=field(f,"Helper level · 0 removes",String.valueOf(u.helperLevel>0?u.helperLevel:u.isBuilder()?ledger.boosts.apprenticeLevel:ledger.boosts.labLevel),true);
        EditText mins=field(f,"Active minutes left · 0 if resting",String.valueOf(Math.max(0,(u.helperEnd-System.currentTimeMillis()+59999)/60000)),true);
        EditText cooldown=field(f,"Next session in minutes · 0 if unknown",String.valueOf(Math.max(0,(u.helperNext-System.currentTimeMillis()+59999)/60000)),true);
        CheckBox recurrent=new CheckBox(this);recurrent.setText("Repeat assignment enabled in-game");recurrent.setTextColor(ink);recurrent.setChecked(u.helperRecurrent);f.addView(recurrent);
        ScrollView sc=new ScrollView(this);sc.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle(u.name+" · helper").setView(sc).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{int l=Integer.parseInt(level.getText().toString()),m=Integer.parseInt(mins.getText().toString()),c=Integer.parseInt(cooldown.getText().toString());if(l<0||l>12||m<0||m>60||c<0||c>1440||(c>0&&c<m))throw new IllegalArgumentException();long t=System.currentTimeMillis();ledger.settle(t);
            for(Upgrade other:ledger.upgrades)if(other==u||(l>0&&other.group.equals(u.group))){other.helperLevel=0;other.helperEnd=0;other.helperNext=0;other.helperRecurrent=false;}
            u.helperLevel=l;u.helperEnd=m>0?t+m*60000L:0;u.helperNext=c>0?t+c*60000L:m>0?u.helperEnd+22*3600000L:0;u.helperRecurrent=l>0&&recurrent.isChecked();if(l>0){if(u.isBuilder()){ledger.boosts.apprenticeLevel=l;ledger.boosts.apprenticeReady=u.helperNext;}else{ledger.boosts.labLevel=l;ledger.boosts.labReady=u.helperNext;}}changed();d.dismiss();
        }catch(IllegalArgumentException e){level.setError("Level 0–12, active 0–60 min, next 0–1440 min and after active session");}}));d.show();
    }
    private void about() {
        new AlertDialog.Builder(this).setTitle("Made for your village")
            .setMessage("Clash Ledger is an open-source, offline upgrade tracker. Import a new export when you make changes in-game.\n\nArtwork © Supercell, sourced through the ClashKing asset collection. Barlow Condensed by Jeremy Tribby (SIL Open Font License).\n\nThis material is unofficial and is not endorsed by Supercell. For more information see Supercell's Fan Content Policy.")
            .setPositiveButton("Done",null).setNeutralButton("Fan Content Policy",(d,w)->startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://supercell.com/en/fan-content-policy/")))).show();
    }
    private void changed() { ledger.save(); Reminders.sync(this,false); UpgradeWidget.refresh(this); build(); }
    private LinearLayout form() { LinearLayout l = column(); l.setPadding(dp(22),dp(8),dp(22),dp(10)); return l; }
    private EditText field(LinearLayout form,String label,String value,boolean number) {
        space(form,12); form.addView(text(label,12,muted,false)); space(form,6); EditText e = new EditText(this); e.setText(value); e.setTextColor(ink); e.setSingleLine(true); e.setTextSize(15); e.setPadding(dp(12),dp(12),dp(12),dp(12)); e.setBackground(panel(surface,surface,line,10));
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS); form.addView(e); return e;
    }
    private void settingsMenu() {
        new AlertDialog.Builder(this).setTitle("Your village, your way")
            .setItems(new String[]{"Village settings", "Boosts & helpers", "Countdown format", "Alarms & notifications", "About & artwork"},(d,w)->{if(w==0)settings();else if(w==1)boostOptions();else if(w==2)displayOptions();else if(w==3)reminderOptions();else about();}).setNegativeButton("Close",null).show();
    }
    private void updateReminderStatus() {
        if(reminderStatusView==null)return;
        ReminderSettings options=new ReminderSettings(this);
        String message;
        if(!options.enabled)message="Reminders are off. Turn them on to track future completions.";
        else if(!Reminders.allowed(this))message="Notifications are blocked by Android. Allow them in notification settings below.";
        else {
            android.app.NotificationChannel channel=getSystemService(android.app.NotificationManager.class).getNotificationChannel(options.channelId());
            if(channel!=null&&channel.getImportance()==android.app.NotificationManager.IMPORTANCE_NONE)message="This alert channel is blocked. Open sound & notification settings below.";
            else message=Reminders.exact(this)?"Precise timing is enabled. Alerts follow your saved upgrade timers.":"Approximate timing. Android may delay alerts; enable precise alarms below.";
        }
        reminderStatusView.setText(message);
    }
    private void saveReminderOptions(ReminderSettings options) {
        options.save(); Reminders.ensureChannel(this,options);
        if(!options.enabled)getSystemService(android.app.NotificationManager.class).cancelAll();
        Reminders.sync(this,true); updateReminderStatus();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==101) { Reminders.sync(this,false); updateReminderStatus(); }
    }
    private void reminderOptions() {
        ReminderSettings options=new ReminderSettings(this);
        LinearLayout f=form();
        f.addView(text("Know when your next builder is free.",16,ink,true)); space(f,10);
        Switch enabled=new Switch(this); enabled.setText("Enable reminders"); enabled.setTextColor(ink); enabled.setTextSize(15); enabled.setPadding(0,dp(10),0,dp(10)); enabled.setChecked(options.enabled); f.addView(enabled,lp(-1,52));
        reminderStatusView=text("",12,muted,false); reminderStatusView.setLineSpacing(dp(3),1); f.addView(reminderStatusView); space(f,18);
        f.addView(label("ALERT STYLE",gold)); space(f,8);
        Spinner mode=new Spinner(this); String[] modes={"Quiet notification", "Notification sound", "Alarm tone · up to 1 minute"}; String[] values={"silent","notification","alarm"};
        mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));
        for(int i=0;i<values.length;i++)if(values[i].equals(options.mode))mode.setSelection(i);
        f.addView(mode,lp(-1,48));
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id) {
                if(!options.mode.equals(values[position])) { options.mode=values[position]; saveReminderOptions(options); }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        CheckBox vibration=new CheckBox(this); vibration.setText("Vibrate"); vibration.setTextColor(ink); vibration.setChecked(options.vibration); f.addView(vibration,lp(-1,48));
        vibration.setOnCheckedChangeListener((b,checked)->{options.vibration=checked;saveReminderOptions(options);});
        space(f,12); f.addView(label("NOTIFY ME WHEN THESE FINISH",gold)); space(f,8);
        for(String group:Upgrade.GROUPS) {
            CheckBox category=new CheckBox(this); category.setText(group); category.setTextColor(ink); category.setChecked(options.categories.contains(group)); f.addView(category,lp(-1,44));
            category.setOnCheckedChangeListener((b,checked)->{if(checked)options.categories.add(group);else options.categories.remove(group);saveReminderOptions(options);});
        }
        CheckBox potion=new CheckBox(this); potion.setText("Potions, snacks & event expiry"); potion.setTextColor(ink); potion.setChecked(options.potion); f.addView(potion,lp(-1,44));
        potion.setOnCheckedChangeListener((b,checked)->{options.potion=checked;saveReminderOptions(options);});
        enabled.setOnCheckedChangeListener((b,checked)->{
            options.enabled=checked;saveReminderOptions(options);
            if(checked && android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},101);
        });
        space(f,12);
        f.addView(button("Precise alarm timing",()->{
            if(android.os.Build.VERSION.SDK_INT>=31) {
                try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+getPackageName()))); }
                catch(android.content.ActivityNotFoundException e) { Toast.makeText(this,"Open Android app settings → Alarms & reminders",Toast.LENGTH_LONG).show(); }
            } else Toast.makeText(this,"Precise timing is supported on this Android version.",Toast.LENGTH_SHORT).show();
        }),lp(-1,48)); space(f,8);
        f.addView(button("Sound & notification settings",()->{
            Reminders.ensureChannel(this,options);
            android.content.Intent intent=new android.content.Intent(Reminders.allowed(this)?android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS:android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,options.channelId());
            try { startActivity(intent); } catch(android.content.ActivityNotFoundException e) { Toast.makeText(this,"Open Android app settings → Notifications",Toast.LENGTH_LONG).show(); }
        }),lp(-1,48)); space(f,8);
        f.addView(button("Test reminder in 10 seconds",()->{
            if(!options.enabled) { Toast.makeText(this,"Enable reminders first.",Toast.LENGTH_SHORT).show(); return; }
            if(!Reminders.allowed(this)) { saveReminderOptions(options); Toast.makeText(this,"Allow notifications, then try the test again.",Toast.LENGTH_LONG).show(); return; }
            Reminders.test(this); Toast.makeText(this,Reminders.exact(this)?"Test scheduled for 10 seconds. You can leave the app.":"Test scheduled. Android may delay it without precise alarm access.",Toast.LENGTH_LONG).show();
        }),lp(-1,48)); space(f,14);
        f.addView(text("Alerts use your last imported or edited timers. Import again after changes in-game. Sound and vibration follow your Android volume, channel, and Do Not Disturb settings. Already-completed imports do not trigger alerts.",12,muted,false));
        ScrollView scroll=new ScrollView(this); scroll.addView(f);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Alarms & notifications").setView(scroll).setPositiveButton("Done",null).create();
        dialog.setOnDismissListener(d->reminderStatusView=null); dialog.show(); updateReminderStatus();
    }
    private void settings() {
        LinearLayout f=form(); EditText name=field(f,"Village name",ledger.village,false), count=field(f,"Available builders (1–7)",String.valueOf(ledger.builders),true);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Your village").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(x -> dialog.getButton(-1).setOnClickListener(v -> { try { int n=Integer.parseInt(count.getText().toString()); if(n<1||n>7) throw new IllegalArgumentException(); if(n<ledger.busy(System.currentTimeMillis())) { count.setError("Finish or remove busy builder timers first"); return; } String s=name.getText().toString().trim(); if(s.isEmpty()||s.length()>40) { name.setError("Use 1–40 characters"); return; } ledger.village=s; ledger.builders=n; changed(); dialog.dismiss(); } catch(IllegalArgumentException e) { count.setError("Enter 1–7"); } })); dialog.show();
    }
    private void editor(Upgrade existing) {
        LinearLayout f=form();
        EditText name=field(f,"Upgrade name",existing==null?"":existing.name,false);
        Spinner group=new Spinner(this); String[] groups=Upgrade.GROUPS; group.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,groups));
        if(existing!=null) for(int i=0;i<groups.length;i++) if(groups[i].equals(existing.group)) group.setSelection(i);
        f.addView(text("Category",12,muted,false)); f.addView(group);
        EditText level=field(f,"Target level",existing==null?"":String.valueOf(existing.level),true);
        long remaining=existing==null?0:existing.remaining(System.currentTimeMillis(),ledger.boostUntil);
        long mins=(remaining+59999)/60000;
        f.addView(text("Work remaining at NORMAL speed. If boosted, enter the time before the boost, or convert the remaining work. Use 0 to mark ready.",12,muted,false));
        EditText days=field(f,"Days",String.valueOf(mins/1440),true), hours=field(f,"Hours (0–23)",String.valueOf(mins/60%24),true), minutes=field(f,"Minutes (0–59)",String.valueOf(mins%60),true);
        ScrollView sc=new ScrollView(this); sc.addView(f);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"Add upgrade":"Edit upgrade").setView(sc).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(x -> dialog.getButton(-1).setOnClickListener(v -> {
            try {
                String n=name.getText().toString().trim(); if(n.isEmpty()||n.length()>60) { name.setError("Use 1–60 characters"); return; }
                int l=Integer.parseInt(level.getText().toString()), dd=Integer.parseInt(days.getText().toString()), hh=Integer.parseInt(hours.getText().toString()), mm=Integer.parseInt(minutes.getText().toString());
                if(l<1||l>999||dd<0||dd>365||hh<0||hh>23||mm<0||mm>59) throw new IllegalArgumentException();
                long work=(dd*1440L+hh*60L+mm)*60000L;
                String g=groups[group.getSelectedItemPosition()]; long now=System.currentTimeMillis();
                int busy=0; for(Upgrade u:ledger.upgrades) if(u!=existing&&!u.goblin&&u.group.equals(g)&&u.remaining(now,ledger.boostUntil)>0) busy++;
                int capacity=g.equals("Builders")?ledger.builders:g.equals("Builder Base")?2:1;
                if(work>0&&busy>=capacity&&(existing==null||!existing.goblin)) { Toast.makeText(this,"All "+g.toLowerCase(java.util.Locale.ROOT)+" slots are busy. Edit or remove an existing timer.",Toast.LENGTH_LONG).show(); return; }
                Upgrade u=new Upgrade(existing==null?UUID.randomUUID().toString():existing.id,n,g,l,work,now);
                u.boosts=ledger.boosts;
                if(existing!=null) { if(g.equals(existing.group)){u.helperLevel=existing.helperLevel;u.helperEnd=existing.helperEnd;u.helperNext=existing.helperNext;u.helperRecurrent=existing.helperRecurrent;} u.goblin=existing.goblin; if(work==mins*60000L && g.equals(existing.group)) { u.work=existing.remaining(now,ledger.boostUntil); u.total=existing.total; } ledger.upgrades.set(ledger.upgrades.indexOf(existing),u); } else ledger.upgrades.add(u);
                changed(); dialog.dismiss();
            } catch(IllegalArgumentException e) { Toast.makeText(this,"Enter level 1–999, days 0–365, hours 0–23 and minutes 0–59.",Toast.LENGTH_LONG).show(); }
        })); dialog.show();
    }
    private void importJson() { importJson(""); }
    private void importJson(String initial) {
        LinearLayout f = form();
        f.addView(text("In Clash of Clans: Settings → More Settings → Data Export → Copy. Paste the full export below.", 14, ink, false));
        EditText input = new EditText(this);
        input.setTextColor(ink); input.setHint("{\"tag\":\"#…\",\"timestamp\":…}"); input.setHintTextColor(muted);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackground(panel(surface,surface,line,12)); input.setPadding(dp(12),dp(12),dp(12),dp(12)); input.setTypeface(Typeface.MONOSPACE); input.setTextSize(12); input.setMinLines(5); input.setMaxLines(9);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1_000_001)});
        input.setText(initial);
        f.addView(button("Paste from clipboard", () -> {
            android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
            android.content.ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) { input.setError("Copy the village export first."); return; }
            CharSequence value = clip.getItemAt(0).getText();
            if (value == null) { input.setError("Clipboard has no JSON text."); return; }
            input.setText(value); input.setError(null);
        }));
        space(f,12); f.addView(input); space(f,12);
        f.addView(text("Import replaces the saved village timers and boost with this snapshot. New in-game actions require a fresh export.", 12, muted, false));
        ScrollView sc = new ScrollView(this); sc.addView(f);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Import village JSON").setView(sc).setNegativeButton("Cancel",null).setNeutralButton("Choose file",(x,w)->startActivityForResult(new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(android.content.Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain"}),700)).setPositiveButton("Preview import",null).create();
        dialog.setOnShowListener(x -> dialog.getButton(-1).setOnClickListener(v -> {
            try {
                org.json.JSONObject names;
                try (java.io.InputStream stream = getAssets().open("item_names.json")) {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = stream.read(buffer)) != -1) bytes.write(buffer,0,count);
                    names = new org.json.JSONObject(bytes.toString("UTF-8"));
                }
                long now = System.currentTimeMillis();
                VillageImport data = new VillageImport(input.getText().toString(), names, now);
                StringBuilder summary = new StringBuilder(data.tag + "\nExported " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(data.timestamp)) + "\n");
                for (String group : Upgrade.GROUPS) {
                    int count=0; for (Upgrade u : data.upgrades) if (u.group.equals(group)) count++;
                    summary.append("\n").append(group).append(": ").append(count);
                }
                summary.append("\n\n").append(data.boosts.summary(now,data.boostUntil));
                for(Upgrade u:data.upgrades)if(u.helperLevel>0)summary.append("\n").append(u.name).append(" · helper L").append(u.helperLevel).append(u.helperEnd>now?" · "+Upgrade.duration(u.helperEnd-now)+" active":" · resting");
                summary.append("\n\nManual event adjustments reset on import.");
                if (ledger.importedAt > data.timestamp && data.tag.equals(ledger.playerTag)) summary.append("\n\nThis export is older than your last import.");
                for (String warning : data.warnings) summary.append("\n\n").append(warning);
                summary.append("\n\nReplace current timers with this export?");
                new AlertDialog.Builder(this).setTitle("Review village import").setMessage(summary.toString()).setNegativeButton("Back",null)
                    .setPositiveButton("Import village",(d,w) -> { ledger.importVillage(data); changed(); dialog.dismiss(); Toast.makeText(this,"Imported " + data.upgrades.size() + " upgrade timers",Toast.LENGTH_LONG).show(); }).show();
            } catch (org.json.JSONException | IllegalArgumentException e) {
                input.setError("Invalid village export: " + e.getMessage());
            } catch (java.io.IOException e) {
                input.setError("Could not load item names. Please reinstall the app.");
            }
        }));
        dialog.show();
    }
    private void pinWidget() {
        AppWidgetManager m=getSystemService(AppWidgetManager.class);
        if(m.isRequestPinAppWidgetSupported()) m.requestPinAppWidget(new ComponentName(this,UpgradeWidget.class),null,null);
        else new AlertDialog.Builder(this).setTitle("Add your widget").setMessage("Long-press an empty area on your home screen, choose Widgets, then select Clash Ledger.").setPositiveButton("Got it",null).show();
    }
}
