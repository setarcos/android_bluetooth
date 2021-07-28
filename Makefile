TARGET=Blue
BUILD=build
DOTPATH=com/pluto/myfirstapp
SDK="/usr/lib/android-sdk/"
BUILD_TOOLS="$(SDK)/build-tools/debian"# 27.0.1
PLATFORM="$(SDK)/platforms/android-23/"

$(BUILD)/$(TARGET).apk: $(BUILD)/$(TARGET).aligned.apk
	apksigner sign --ks ../keystore.jks --ks-key-alias androidkey --ks-pass pass:android \
      --key-pass pass:android --out $@ $<

$(BUILD)/$(TARGET).aligned.apk: $(BUILD)/$(TARGET).unsigned.apk
	zipalign -f -p 4 $< $@

$(BUILD)/$(TARGET).unsigned.apk: $(BUILD)/apk/classes.dex
	aapt package -f -M AndroidManifest.xml -S res/ -I "${PLATFORM}/android.jar" \
      -F $@ "$(BUILD)/apk/"
$(BUILD)/apk/classes.dex:
	mkdir -p $(BUILD)/gen $(BUILD)/obj $(BUILD)/apk
	aapt package -f -m -J $(BUILD)/gen/ -S res -M AndroidManifest.xml \
	   	-I "$(PLATFORM)/android.jar"
	javac -source 1.7 -target 1.7 -bootclasspath "$(JAVA_HOME)/jre/lib/rt.jar" \
      -classpath "$(PLATFORM)/android.jar" -d $(BUILD)/obj \
      $(BUILD)/gen/$(DOTPATH)/R.java src/$(DOTPATH)/MainActivity.java
	$(BUILD_TOOLS)/dx --dex --output=$@ $(BUILD)/obj/
