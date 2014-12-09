INSTALL_STAMP=.install.stamp

all: install
install: $(INSTALL_STAMP)

$(INSTALL_STAMP):
	npm install
	touch $(INSTALL_STAMP)

test: install
	@./node_modules/mocha-phantomjs/bin/mocha-phantomjs test/index.html

clean:
	rm -rf node_modules/ $(INSTALL_STAMP)

.PHONY: test
