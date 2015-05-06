(function() {
  var module;

  module = angular.module('docflow.widget.tags', ['docflow.config']);

  module.directive('docflowWidgetTags', [
    '$docflow', '$docflowConfig', '$http', '$timeout', '$log', (function($docflow, $docflowConfig, $http, $timeout, $log) {
      return {
        scope: true,
        restrict: 'A',
        require: 'ngModel',
        link: (function($scope, element, attrs, ngModel) {
          var ie, input, options, params, select2Init;
          params = {};
          if (attrs.docflowWidgetTags) {
            angular.extend(params, $scope.$eval(attrs.docflowWidgetTags));
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
            $docflow.dialogCreate(params.docType, 'item', 'dict').then(function(value) {
              if (value) {
                ngModel.$setViewValue(value);
                ngModel.$render();
              }
            });
          };
          ie = element.find('[docflow-select2]');
          input = ie.length > 0 ? $(ie[0]) : element;
          options = {
            tags: null,
            ajax: {
              cache: false,
              url: "/api/list/" + params.docType + "?&" + $docflowConfig.apiParams,
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
                  var i, list, p, term, _i, _j, _len, _len1;
                  data.list = data.list || [];
                  list = data.list;
                  if (options.text) {
                    for (_i = 0, _len = list.length; _i < _len; _i++) {
                      p = list[_i];
                      p.text = p[options.text];
                      delete p[options.text];
                    }
                  }
                  if (data.query.x) {
                    term = data.query.x.toUpperCase();
                    for (i = _j = 0, _len1 = list.length; _j < _len1; i = ++_j) {
                      p = list[i];
                      if (p.text.toUpperCase() === term) {
                        break;
                      }
                    }
                    if (i === list.length) {
                      list.unshift({
                        id: '_' + term,
                        text: data.query.x
                      });
                    }
                  }
                  return {
                    results: data.list,
                    more: data.query.c > data.query.p
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
            }
          };
          angular.extend(options, params);
          select2Init = false;
          $timeout(function() {
            input.select2(options);
            select2Init = true;
            return ngModel.$render();
          });
          input.change(function() {
            var tag, tags, v, _i, _len;
            v = input.select2('data');
            tags = [];
            for (_i = 0, _len = v.length; _i < _len; _i++) {
              tag = v[_i];
              if (tag.id.substr(0, 1) === '_') {
                tags.push({
                  _u: {
                    tag: {
                      id: params.docType,
                      title: tag.text
                    }
                  }
                });
              } else {
                tags.push({
                  _u: {
                    tag: tag.id
                  }
                });
              }
            }
            ngModel.$setViewValue(tags);
            $scope.$apply();
          });
          ngModel.$render = (function() {
            var list, record, v, _i, _len;
            v = ngModel.$viewValue;
            if (select2Init) {
              if (v) {
                if (!angular.isArray(v)) {
                  $log.error('Expected list of tags');
                  return;
                }
                list = [];
                for (_i = 0, _len = v.length; _i < _len; _i++) {
                  record = v[_i];
                  if (record.tag) {
                    list.push(record.tag);
                  }
                }
                input.select2('data', list);
              } else {
                input.select2('data', null);
              }
            }
          });
        })
      };
    })
  ]);

}).call(this);
