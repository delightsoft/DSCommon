(function() {
  var module;

  module = angular.module('docflow.widget.subtable', ['docflow.config']);

  module.directive('docflowHideValidation', [
    (function() {
      return {
        scope: true,
        restrict: 'A',
        link: (function($scope, element, attrs) {
          attrs.$observe('docflowHideValidation', (function(v) {
            $scope.hideValidation = v === 'true';
          }));
        })
      };
    })
  ]);

  module.directive('docflowWidgetSubtable', [
    '$docflow', '$http', '$timeout', '$log', (function($docflow, $http, $timeout, $log) {
      return {
        scope: true,
        restrict: 'A',
        require: 'ngModel',
        link: (function($scope, element, attrs, ngModel) {
          var params, required;
          params = {};
          $scope.sortableOptions = {
            handle: '.fa-ellipsis-v',
            axis: 'y'
          };
          if (attrs.docflowWidgetSubtable) {
            angular.extend(params, $scope.$eval(attrs.docflowWidgetSubtable));
          }
          if (!params.field) {
            $log.error('Missing \'field\' attribute');
            return;
          }
          required = angular.isDefined(attrs.required);
          $scope.$parent.$watch("item._n." + params.field, (function(newLinePrototype) {
            var item, list, modelUpdated, newline, watchNewline, watchSourceCollection;
            if (typeof watchNewline === "function") {
              watchNewline();
            }
            watchNewline = null;
            if (typeof watchSourceCollection === "function") {
              watchSourceCollection();
            }
            watchSourceCollection = null;
            if (!newLinePrototype) {
              $scope.list = $scope.$parent.item ? $scope.item[params.field] : [];
              return;
            }
            $scope.list = list = (function() {
              var _i, _len, _ref, _results;
              _ref = $scope.$parent.item[params.field];
              _results = [];
              for (_i = 0, _len = _ref.length; _i < _len; _i++) {
                item = _ref[_i];
                _results.push(item);
              }
              return _results;
            })();
            $scope.newline = newline = angular.copy(newLinePrototype);
            list.push(newline);
            watchNewline = $scope.$watch('newline', (function(n) {
              if (newline) {
                newline = null;
              } else {
                $scope.$parent.item[params.field].push($scope.newline);
                $scope.newline = newline = angular.copy(newLinePrototype);
                list.push(newline);
              }
            }), true);
            $scope.remove = function(item) {
              var ind, model;
              if (item !== newline) {
                model = $scope.$parent.item[params.field];
                ind = model.indexOf(item);
                model.splice(ind, 1);
                return list.splice(ind, 1);
              }
            };
            modelUpdated = (function(newModel) {
              $scope.list = list = (function() {
                var _i, _len, _results;
                _results = [];
                for (_i = 0, _len = newModel.length; _i < _len; _i++) {
                  item = newModel[_i];
                  _results.push(item);
                }
                return _results;
              })();
              list.push($scope.newline);
              $scope.$parent.item[params.field] = newModel;
            });
            watchSourceCollection = $scope.$parent.$watch("item." + params.field, (function(fld) {
              var e, i, _i, _len;
              if (required) {
                ngModel.$setValidity('required', fld.length !== 0);
              }
              if (fld.length + 1 !== list.length) {
                modelUpdated(fld);
                return;
              }
              for (i = _i = 0, _len = fld.length; _i < _len; i = ++_i) {
                e = fld[i];
                if (e !== list[i]) {
                  modelUpdated(fld);
                  return;
                }
              }
            }), true);
          }), false);
        })
      };
    })
  ]);

}).call(this);
