(function() {
  var LinkedDocumentTab, module,
    __hasProp = {}.hasOwnProperty,
    __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor(); child.__super__ = parent.prototype; return child; };

  module = angular.module('docflow.ui.tabLinkedDocument', ['docflow.config', 'docflow.ui.editor', 'docflow.ui.utils']);

  LinkedDocumentTab = null;

  module.config([
    '$docflowEditorProvider', (function($docflowEditorProvider) {
      $docflowEditorProvider.registerTabTemplate('linkedDocument', (function() {
        return LinkedDocumentTab;
      }));
    })
  ]);

  module.run([
    '$docflowEditor', '$docflowUtils', '$docflowConfig', (function($docflowEditor, $docflowUtils, $docflowConfig) {
      LinkedDocumentTab = (function(_super) {
        var _class;

        __extends(LinkedDocumentTab, _super);

        function LinkedDocumentTab() {
          return _class.apply(this, arguments);
        }

        _class = (function(editor, tabName, tabConfig) {
          $docflowEditor.Tab.call(this, editor, tabName, tabConfig);
          this.template = 'form';
        });

        LinkedDocumentTab.prototype.splitDoc = (function(doc) {
          var canCreate, docConfig, _ref, _ref1;
          this.visible = true;
          this.isNew = false;
          this.id = doc.id;
          if (this.canCreate = canCreate = angular.isDefined((_ref = doc._u) != null ? _ref[this.name] : void 0)) {
            this.item = doc._u[this.name];
            delete doc._u[this.name];
          } else {
            this.item = doc[this.name];
          }
          if (angular.isObject(this.item)) {
            $docflowUtils.processDocumentBeforeEditing(this.item);
          } else if (canCreate) {
            this.docConfig = docConfig = $docflowConfig.docs[this.docType];
            if (!docConfig) {
              throw Error("DocType " + this.docType + " not in docflowConfig");
            }
            if (!docConfig._n) {
              throw Error("Missing _n for type '" + this.docType + "' in docflowConfig");
            }
            if ((_ref1 = this.config.options) != null ? _ref1.showNew : void 0) {
              this.item = angular.copy(docConfig._n);
              this.isNew = true;
            } else {
              this.visible = false;
            }
          } else {
            this.visible = false;
          }
          delete doc[this.name];
          if (this.scope) {
            this.scope.item = this.item;
          }
        });

        LinkedDocumentTab.prototype.mergeDoc = (function(doc) {
          var item, sid;
          doc[this.name] = item = $docflowUtils.buildDocumentUpdate(this.item);
          sid = $docflowUtils.splitFullId(this.id);
          if (sid.id) {
            item.subj = this.id;
          }
        });

        LinkedDocumentTab.prototype.action = (function(action, params) {
          this.callParent('action', action, params).then((function(_this) {
            return function(resp) {
              var root, _ref;
              root = resp.doc.subj;
              if (angular.isDefined((_ref = root._u) != null ? _ref[_this.name] : void 0)) {
                if (root._u[_this.name]) {
                  root._u[_this.name] = resp.doc;
                }
              } else {
                if (root[_this.name]) {
                  root[_this.name] = resp.doc;
                }
              }
              delete resp.doc.subj;
              _this.editor.setDoc(root);
            };
          })(this));
        });

        LinkedDocumentTab.prototype.createLinkedDoc = (function() {
          if (this.item) {
            throw Error("Cannot create linked document: Field " + this.name + " already not null.");
          }
          if (!this.canCreate) {
            throw Error("Cannot create linked document: User has no right to modify field " + this.name + ".");
          }
          this.item = angular.copy(this.docConfig._n);
          this.isNew = true;
          this.visible = true;
          this.modified = true;
          this.editor.selectTab(this.name);
        });

        return LinkedDocumentTab;

      })($docflowEditor.Tab);
    })
  ]);

}).call(this);
