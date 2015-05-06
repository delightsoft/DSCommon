(function() {
  var module;

  module = angular.module('docflow.widget.selector', ['docflow.config', 'docflow.widget.select2']);

  module.directive('docflowWidgetSelector', [
    '$docflow', '$docflowConfig', '$http', '$timeout', '$log', (function($docflow, $docflowConfig, $http, $timeout, $log) {
      var docType;
      docType = 'Position';
      return {
        scope: true,
        restrict: 'A',
        require: 'ngModel',
        link: function($scope, element, attrs, ngModel) {
          var ie, input, options, params, select2Init, url;
          params = {};
          if (attrs.docflowWidgetSelector) {
            angular.extend(params, $scope.$eval(attrs.docflowWidgetSelector));
          }
          if (!params.docType) {
            $log.error('Missing \'type\' attribute');
            return;
          }
          if (!params.template) {
            $log.error('Missing \'template\' attribute');
            return;
          }
          $scope.createNew = function() {
            $docflow.createDocDialog(params.docType, {
              resultTemplate: 'dict'
            }).then(function(value) {
              if (value) {
                ngModel.$setViewValue(value);
                ngModel.$render();
              }
            });
          };
          ie = element.find('[docflow-select2]');
          input = ie.length > 0 ? $(ie[0]) : element;
          url = "/api/list/" + params.docType + "?" + $docflowConfig.apiParams;
          if (angular.isDefined(params.filter) && params.filter.length > 0) {
            url = ("/api/list/" + params.docType + "?" + $docflowConfig.apiParams) + ("&" + params.filter);
          }
          options = {
            cache: false,
            url: url,
            dateType: 'json',
            quiteMillis: 300,
            data: (function(term, page) {
              var res;
              res = {
                t: params.template
              };
              if (term) {
                res.x = term;
              }
              if (page) {
                res.p = page;
                res.c = page + 1;
              }
              return res;
            }),
            results: (function(data, page) {
              var res, v;
              res = (function() {
                var p, _i, _len, _ref;
                if (data.list !== null) {
                  if (options.text) {
                    _ref = data.list;
                    for (_i = 0, _len = _ref.length; _i < _len; _i++) {
                      p = _ref[_i];
                      p.text = p[options.text];
                      delete p[options.text];
                    }
                  }
                  return {
                    results: data.list,
                    more: data.query.c > data.query.p
                  };
                }
                return {
                  results: []
                };
              })();
              if (page === 1 && angular.isDefined(options.first)) {
                v = {
                  id: '',
                  text: options.first
                };
                if (res.results) {
                  res.results.unshift(v);
                } else {
                  res.results = [v];
                }
              }
              return res;
            })
          };
          angular.extend(options, params);
          select2Init = false;
          $timeout(function() {
            input.select2({
              ajax: options
            });
            if (options.readonly) {
              input.select2('readonly', true);
            }
            select2Init = true;
            return ngModel.$render();
          });
          input.change(function() {
            var v;
            v = input.select2('data');
            ngModel.$setViewValue(v.id ? v : null);
            $scope.$apply();
          });
          ngModel.$render = function() {
            var v;
            v = ngModel.$viewValue;
            if (select2Init) {
              if (!angular.isObject(v)) {
                v = !v ? {
                  id: '',
                  text: options.first
                } : {
                  id: v,
                  text: ''
                };
              }
              input.select2('data', v);
              return;
            }
            input.val(null);
          };
        }
      };
    })
  ]);

}).call(this);
