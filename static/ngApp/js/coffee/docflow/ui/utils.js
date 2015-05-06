(function() {
  var module;

  module = angular.module('docflow.ui.utils', []);

  module.provider('$docflowUtils', [
    (function() {
      var buildDocumentUpdate, buildDocumentsListUpdate, processDocumentBeforeEditing, processDocumentsListBeforeEditing, updateModel, utils;
      utils = this;
      this.$get = (function() {
        return utils;
      });
      utils.docType = function(docId) {
        var d;
        d = docId.indexOf('@');
        if (d > 0) {
          return docId.substr(0, d);
        } else {
          return docId;
        }
      };
      utils.splitFullId = (function(fullId) {
        var d;
        d = fullId.indexOf('@');
        if (d > 0) {
          return {
            docType: fullId.substr(0, d),
            id: fullId.substr(d + 1)
          };
        }
        return {
          docType: fullId
        };
      });
      utils.isNewDoc = function(docId) {
        var d;
        d = docId.indexOf('@');
        return d === -1;
      };
      utils.processDocumentsListBeforeEditing = processDocumentsListBeforeEditing = (function(list, level) {
        var i, item, _i, _len;
        level = angular.isDefined(level) ? level + 1 : 0;
        if (list) {
          i = 0;
          for (_i = 0, _len = list.length; _i < _len; _i++) {
            item = list[_i];
            processDocumentBeforeEditing(item, level);
            if (level > 0) {
              item.i = i++;
            }
          }
        }
      });
      utils.processDocumentBeforeEditing = processDocumentBeforeEditing = (function(item, level) {
        var k, v, _ref;
        if (!(item != null ? item._u : void 0)) {
          return;
        }
        level = angular.isDefined(level) ? level + 1 : 0;
        _ref = item._u;
        for (k in _ref) {
          v = _ref[k];
          if (angular.isArray(v)) {
            processDocumentsListBeforeEditing(v, level);
            item[k] = v;
          } else if (angular.isObject(v)) {
            processDocumentBeforeEditing(v, level);
            item[k] = v;
          }
        }
      });
      utils.buildDocumentsListUpdate = buildDocumentsListUpdate = (function(list) {
        var i;
        return (function() {
          var _i, _len, _results;
          _results = [];
          for (_i = 0, _len = list.length; _i < _len; _i++) {
            i = list[_i];
            _results.push(buildDocumentUpdate(i));
          }
          return _results;
        })();
      });
      utils.buildDocumentUpdate = buildDocumentUpdate = (function(item) {
        var k, v;
        if (!(item != null ? item._u : void 0)) {
          if (item.id) {
            return item.id;
          } else {
            return {};
          }
        }
        for (k in item) {
          v = item[k];
          if (k !== '_u') {
            if (item._u.hasOwnProperty(k)) {
              if (angular.isArray(v)) {
                item._u[k] = buildDocumentsListUpdate(v);
              } else if (angular.isObject(v)) {
                item._u[k] = buildDocumentUpdate(v);
              } else if (angular.isObject(item._u[k])) {
                item._u[k] = null;
              }
            }
          }
        }
        if (angular.isDefined(item.i)) {
          item._u.i = item.i;
        } else if (angular.isDefined(item.id)) {
          item._u.id = item.id;
          if (angular.isDefined(item.rev)) {
            item._u.rev = item.rev;
          }
        }
        return item._u;
      });
      utils.encodeUriQuery = (function(val, pctEncodeSpaces) {
        return encodeURIComponent(val).replace(/%40/gi, '@').replace(/%3A/gi, ':').replace(/%24/g, '$').replace(/%2C/gi, ',').replace(/%20/g, pctEncodeSpaces ? '%20' : '+');
      });
      utils.updateModel = updateModel = (function(model, update) {
        var arr, i, k, m, u, v, _i, _j, _len, _len1;
        if (!model) {
          return update;
        }
        for (k in model) {
          v = model[k];
          if (k.substr(0, 1) !== '$') {

          } else if (!update.hasOwnProperty(k)) {
            delete model[k];
          } else if (angular.isArray(v)) {
            arr = update[k];
            for (i = _i = 0, _len = arr.length; _i < _len; i = ++_i) {
              u = arr[i];
              for (_j = 0, _len1 = v.length; _j < _len1; _j++) {
                m = v[_j];
                if (m.id === u.id) {
                  updateModel(m, u);
                  arr[i] = m;
                }
              }
            }
            angular.copy(arr, v);
          } else if (angular.isObject(v)) {
            updateModel(v, update[k]);
          } else {
            model[k] = update[k];
          }
        }
        angular.extend(model, update);
        return model;
      });
    })
  ]);

  module.run([
    '$rootScope', '$docflowConfig', '$state', '$location', '$sce', (function($rootScope, $docflowConfig, $state, $location, $sce) {
      $rootScope.$angular = angular;
      $rootScope.$sce = $sce;
      $rootScope.pageTitle = "";
      $rootScope.setPageTitle = (function(title) {
        var _ref;
        $rootScope.pageTitle = title;
        if ((_ref = $state.current) != null) {
          _ref.pageTitle = title;
        }
      });
    })
  ]);

}).call(this);
