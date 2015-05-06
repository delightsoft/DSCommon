(function() {
  var module, oneTimeCalculatedProp, prop;

  module = angular.module('docflow.config', ['docflow.ui.utils', 'ng']);

  prop = (function(obj, prop, func, configurable) {
    Object.defineProperty(obj, prop, {
      configurable: false,
      get: func
    });
  });

  oneTimeCalculatedProp = (function(obj, prop, func, configurable) {
    Object.defineProperty(obj, prop, {
      configurable: true,
      get: (function() {
        var v;
        v = func();
        Object.defineProperty(obj, prop, {
          configurable: !!configurable,
          value: v
        });
        return v;
      })
    });
  });

  module.provider('$docflowConfig', [
    '$docflowUtilsProvider', '$windowProvider', (function($docflowUtilsProvider, $windowProvider) {
      var provider, templateBase;
      provider = this;
      templateBase = 'ngApp';
      this.changeTemplateBase = (function(base) {
        templateBase = base;
      });
      this.docflowConfig = $windowProvider.$get().docflowConfig;
      oneTimeCalculatedProp(this, 'user', ((function(_this) {
        return function() {
          return _this.docflowConfig.user;
        };
      })(this)));
      oneTimeCalculatedProp(this, 'docs', ((function(_this) {
        return function() {
          var actionConfig, actionName, docConfig, docName, _ref, _ref1;
          _ref = _this.docflowConfig.docs;
          for (docName in _ref) {
            docConfig = _ref[docName];
            if (docConfig._n) {
              $docflowUtilsProvider.processDocumentBeforeEditing(docConfig._n);
            }
            _ref1 = docConfig.actions;
            for (actionName in _ref1) {
              actionConfig = _ref1[actionName];
              if (actionConfig.params) {
                actionConfig.params = {
                  _u: actionConfig.params
                };
              }
            }
          }
          return _this.docflowConfig.docs;
        };
      })(this)));
      oneTimeCalculatedProp(this, 'messages', ((function(_this) {
        return function() {
          return _this.docflowConfig.messages || {};
        };
      })(this)));
      this.$get = [
        '$location', ((function(_this) {
          return function($location) {
            oneTimeCalculatedProp(_this, 'templateBase', (function() {
              return templateBase;
            }));
            prop(_this, 'debug', (function() {
              return $location.search().debug;
            }));
            prop(_this, 'user', (function() {
              return $location.search().user;
            }));
            prop(_this, 'tmplParams', (function() {
              var debug, params, res, user;
              params = $location.search();
              res = [];
              if (debug = params.debug) {
                res.push("debug=" + debug);
              }
              if (user = params.user) {
                res.push("user=" + user);
              }
              return res.join('&');
            }));
            prop(_this, 'apiParams', (function() {
              var val;
              if (val = $location.search().user) {
                return "user=" + val;
              } else {
                return '';
              }
            }));
            return _this;
          };
        })(this))
      ];
    })
  ]);

  module.run([
    '$docflowConfig', '$rootScope', '$location', (function($docflowConfig, $rootScope, $location) {
      var res;
      res = {};
      if ($docflowConfig.debug) {
        res.debug = $docflowConfig.debug;
      }
      if ($docflowConfig.user) {
        res.user = $docflowConfig.user;
      }
      $rootScope.$on('$stateChangeSuccess', (function() {
        $location.search(angular.extend($location.search(), res));
        $location.replace();
      }));
    })
  ]);

}).call(this);
