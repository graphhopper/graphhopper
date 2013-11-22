JAVA_SOURCES = $(TMPDIR).sources.list

translate: pre_translate $(OBJC_SOURCES)
        @if [ `cat $(JAVA_SOURCES) | wc -l` -ge 1 ] ; then \
          $(J2OBJC) -sourcepath $(SOURCE_DIR) -d $(BUILD_DIR) \
            `cat $(JAVA_SOURCES)` ; \
        fi

pre_translate: $(BUILD_DIR)
        @rm -f $(JAVA_SOURCES)
        @touch $(JAVA_SOURCES)

$(BUILD_DIR)/%.m $(BUILD_DIR)/%.h: $(SOURCE_DIR)/%.java
        @echo $? >> $(JAVA_SOURCES)

