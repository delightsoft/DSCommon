(function() {
  var module,
    __slice = [].slice,
    __hasProp = {}.hasOwnProperty,
    __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

  module = angular.module('docflow.ui.editor', ['docflow', 'docflow.ui.client', 'docflow.config', 'docflow.ui.utils']);

  module.run([
    '$docflow', '$docflowEditor', (function($docflow, $docflowEditor) {
      $docflow._setEditor($docflowEditor);
    })
  ]);

  module.provider('$docflowEditor', [
    '$docflowUtilsProvider', '$docflowConfigProvider', '$injector', (function($docflowUtilsProvider, $docflowConfigProvider, $injector) {
      var tabScopeExtentions, tabTemplatePlugins;
      tabTemplatePlugins = {};
      tabScopeExtentions = [];
      this.registerTabTemplate = (function(templateName, tabClass) {
        tabTemplatePlugins[templateName] = tabClass;
      });
      this.registerTabScopeExtention = (function(ext) {
        tabScopeExtentions.push(ext);
      });
      this.$get = [
        '$docflow', '$docflowActions', '$docflowClient', '$docflowUtils', '$docflowConfig', '$q', (function($docflow, $docflowActions, $docflowClient, $docflowUtils, $docflowConfig, $q) {
          var Editor, MainTab, Tab;
          this.Tab = Tab = (function() {
            var _class;

            function Tab() {
              return _class.apply(this, arguments);
            }

            _class = (function(editor, tabName, tabConfig) {
              this.editor = editor;
              this.name = tabName;
              this.scope = null;
              this.removeWatcher = null;
              this.visible = true;
              this.config = tabConfig;
              if (tabConfig) {
                this.docType = tabConfig.doc;
                this.template = tabConfig.template;
                this.angularTmpl = "/tmpl/doc/" + this.docType + "?b=" + $docflowConfig.templateBase + "&t=" + this.template + "&item=" + editor.docType + "&tab=" + tabName + "&" + $docflowConfigProvider.tmplParams;
              }
            });

            Tab.prototype.name = null;

            Tab.prototype.controller = [
              '$scope', (function($scope) {
                (function() {
                  var ext, _i, _len;
                  this.scope = $scope;
                  $scope.$on('$destroy', ((function(_this) {
                    return function() {
                      _this.scope = _this.removeWatcher = null;
                    };
                  })(this)));
                  if (this.item) {
                    $scope.item = this.item;
                    this.setWatcher();
                  }
                  for (_i = 0, _len = tabScopeExtentions.length; _i < _len; _i++) {
                    ext = tabScopeExtentions[_i];
                    ext()($scope, this);
                  }
                }).call($scope.$parent.tab);
              })
            ];

            Tab.prototype.setWatcher = (function() {
              if (!this.modified && this.scope && this.item) {
                if (typeof this.removeWatcher === "function") {
                  this.removeWatcher();
                }
                this.removeWatcher = this.scope.$watch('item', ((function(_this) {
                  return function(n, o) {
                    if (n !== o) {
                      _this.modified = true;
                      _this.removeWatcher();
                      _this.removeWatcher = null;
                    }
                  };
                })(this)), true);
              }
            });

            Tab.prototype.splitDoc = (function(doc) {
              var doc_u, fld, item, u, _i, _j, _len, _len1, _ref, _ref1;
              if (!this.config.fields) {
                return;
              }
              this.item = item = {};
              _ref = this.config.fields;
              for (_i = 0, _len = _ref.length; _i < _len; _i++) {
                fld = _ref[_i];
                if (doc.hasOwnProperty(fld)) {
                  item[fld] = doc[fld];
                  delete doc[fld];
                }
              }
              if (doc._u) {
                doc_u = doc._u;
                item._u = u = {};
                _ref1 = this.config.fields;
                for (_j = 0, _len1 = _ref1.length; _j < _len1; _j++) {
                  fld = _ref1[_j];
                  if (doc_u.hasOwnProperty(fld)) {
                    u[fld] = doc_u[fld];
                    delete doc_u[fld];
                  }
                }
              }
              $docflowUtils.processDocumentBeforeEditing(item);
            });

            Tab.prototype.mergeDoc = (function(doc) {
              if (!this.config.fields) {
                return;
              }
              angular.extend(doc, $docflowUtils.buildDocumentUpdate(this.item));
            });

            Tab.prototype._modified = false;

            Object.defineProperty(Tab.prototype, 'modified', {
              get: function() {
                return this._modified;
              },
              set: (function(v) {
                this._modified = v;
                if (v) {
                  this.editor.modified = true;
                }
              })
            });

            Tab.prototype._isNew = void 0;

            Object.defineProperty(Tab.prototype, 'isNew', {
              get: function() {
                if (angular.isDefined(this._isNew)) {
                  return this._isNew;
                } else {
                  return this.editor.isNew;
                }
              },
              set: (function(v) {
                this._isNew = v;
              })
            });

            Tab.prototype.action = (function(action, params) {
              if (!this.item) {
                throw Error('Cannot call action(...) for tab that doesn\'t have .item');
              }
              return $docflow.action(this.item.id, action, {
                params: params,
                r: this.item.rev,
                t: this.template
              });
            });

            Tab.prototype.callParent = function() {
              var args, method;
              method = arguments[0], args = 2 <= arguments.length ? __slice.call(arguments, 1) : [];
              return this.__proto__.__proto__[method].apply(this, args);
            };

            return Tab;

          })();
          MainTab = (function(_super) {
            var _class;

            __extends(MainTab, _super);

            function MainTab() {
              return _class.apply(this, arguments);
            }

            _class = (function(editor) {
              Tab.call(this, editor, "_main");
              this.item = null;
              this.docType = editor.docType;
              this.template = editor.template;
              this.angularTmpl = "/tmpl/doc/" + this.docType + "?b=" + $docflowConfig.templateBase + "&t=mainTab";
              this.editorDlg = null;
            });

            MainTab.prototype.splitDoc = (function(doc) {
              this.visible = true;
              this.item = $docflowUtilsProvider.updateModel(this.item, doc);
              $docflowUtilsProvider.processDocumentBeforeEditing(this.item);
              if (this.scope) {
                this.scope.item = this.item;
              }
            });

            MainTab.prototype.mergeDoc = (function(doc) {
              angular.extend(doc, $docflowUtils.buildDocumentUpdate(this.item));
            });

            MainTab.prototype.action = (function(action, params) {
              this.callParent('action', action, params).then((function(_this) {
                return function(resp) {
                  _this.editor.setDoc(resp.doc);
                };
              })(this));
            });

            return MainTab;

          })(Tab);
          return Editor = (function() {
            var _class;

            function Editor() {
              return _class.apply(this, arguments);
            }

            _class = (function(docId, options) {
              var docConfig, docType, mainTab, tabConfig, tabName, tabs, template, templateConfig, templateTabPlugin, _name, _ref, _ref1;
              this.docId = docId;
              this.isNew = $docflowUtilsProvider.isNewDoc(docId);
              this.docType = docType = $docflowUtilsProvider.docType(docId);
              this.template = template = 'form';
              if (options) {
                if (options.t) {
                  this.template = template = options.t;
                }
                this.resultTemplate = options.resultTemplate ? options.resultTemplate : template;
              }
              this.docConfig = docConfig = $docflowConfig.docs[docType];
              this.modified = false;
              this.upload = 0;
              if (!docConfig) {
                throw Error("Doctype " + docType + " not in docflowConfig");
              }
              this.templateConfig = templateConfig = (_ref = docConfig.templates) != null ? _ref[template] : void 0;
              if (!templateConfig) {
                throw Error("Doctype " + docType + " has no template " + this.template + " in docflowConfig");
              }
              mainTab = new MainTab(this);
              this.tabs = tabs = {};
              this.selectedTab = tabs[mainTab.name] = mainTab;
              if (templateConfig.tabs) {
                _ref1 = templateConfig.tabs;
                for (tabName in _ref1) {
                  tabConfig = _ref1[tabName];
                  templateTabPlugin = typeof tabTemplatePlugins[_name = tabConfig.template] === "function" ? tabTemplatePlugins[_name]() : void 0;
                  tabs[tabName] = templateTabPlugin ? new templateTabPlugin(this, tabName, tabConfig) : new Tab(this, tabName, tabConfig);
                }
              }
            });

            Editor.Tab = Tab;

            Editor.prototype.scope = null;

            Editor.prototype.controller = (function($scope) {
              this.scope = $scope;
              $scope.doc = this.doc;
              $scope.tab = this.selectedTab;
              $scope.$on('$destroy', ((function(_this) {
                return function() {
                  _this.scope = null;
                };
              })(this)));
            });

            Editor.prototype.setDoc = (function(doc) {
              var docToSplit, mainTab, tab, tabName, _ref, _ref1;
              if (doc) {
                this.doc = doc;
                docToSplit = angular.copy(doc);
                mainTab = this.tabs._main;
                _ref = this.tabs;
                for (tabName in _ref) {
                  tab = _ref[tabName];
                  if (tab !== mainTab) {
                    tab.splitDoc(docToSplit);
                  }
                }
                mainTab.splitDoc(docToSplit);
                if (this.scope) {
                  this.scope.doc = doc;
                }
                if (!this.selectedTab.visible) {
                  this.selectTab('_main');
                }
              }
              this.modified = false;
              _ref1 = this.tabs;
              for (tabName in _ref1) {
                tab = _ref1[tabName];
                tab.modified = false;
                tab.setWatcher();
              }
            });

            Editor.prototype.loadOrCreate = (function() {
              var deferred;
              if (this.isNew && !this.docConfig.newInstance) {
                if (!this.docConfig._n) {
                  throw Error("Missing _n for type '" + this.docType + "' in docflowConfig");
                }
                this.setDoc(this.docConfig._n);
                this.modified = true;
                return this;
              }
              deferred = $q.defer();
              (this.isNew ? $docflowClient.action(this.docType, 'newInstance', {
                t: 'form'
              }).then((function(resp) {
                return resp.result;
              })) : $docflowClient.get(this.docId, {
                t: 'form'
              })).then((function(_this) {
                return function(doc) {
                  _this.setDoc(doc);
                  deferred.resolve(_this);
                };
              })(this));
              return deferred.promise;
            });

            Editor.prototype.selectTab = (function(tabName, safe) {
              var tab;
              tab = this.tabs[tabName];
              if (!tab) {
                if (safe) {
                  return;
                }
                throw Error("Tab with name " + tabName + " not found");
              }
              if (!tab.visible) {
                if (safe) {
                  return;
                }
                throw Error("Tab " + tabName + " is not visible");
              }
              this.selectedTab = tab;
              if (this.scope) {
                this.scope.tab = tab;
              }
            });

            Editor.prototype.extendScope = (function($scope) {
              return $scope.editor = editor;
            });

            Editor.prototype.setEditorDialog = (function(dlg) {
              this.editorDlg = dlg;
            });

            Editor.prototype.save = (function() {
              var doc, tab, tabName, _ref;
              if (!this.modified) {
                return;
              }
              doc = {};
              _ref = this.tabs;
              for (tabName in _ref) {
                tab = _ref[tabName];
                if (tab.modified) {
                  tab.mergeDoc(doc);
                }
              }
              return $docflowClient.action(this.doc.id, (this.isNew ? 'create' : 'update'), {
                doc: doc,
                r: this.doc.rev,
                t: 'form',
                ot: this.resultTemplate ? this.resultTemplate : 'form'
              }).then((function(_this) {
                return function(resp) {
                  if (_this.editorDlg) {
                    _this.editorDlg.close(resp.doc);
                  } else if (_this.isNew) {
                    $docflowActions.setPreserveActionResultOverUIStateTransition();
                    $docflow.openDoc(resp.doc.id, {
                      replace: true
                    });
                  } else {
                    _this.setDoc(resp.doc);
                  }
                };
              })(this));
            });

            Editor.prototype.cancel = (function() {
              if (this.editorDlg) {
                this.editorDlg.dismiss();
              } else if (this.isNew) {
                $docflow.navigateBack();
              } else {
                this.setDoc(this.doc);
              }
            });

            return Editor;

          })();
        })
      ];
    })
  ]);

}).call(this);
