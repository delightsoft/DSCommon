(function() {
  var module;

  module = angular.module('docflow.ui.form', []);

  module.directive('docflowForm', [
    (function() {
      return {
        restrict: 'A',
        scope: false,
        link: function($scope, element, attrs) {
          var u;
          $scope.v = (function(fld, item) {
            return (item != null ? item.hasOwnProperty(fld) : void 0) && !u(fld, item);
          });
          $scope.u = u = (function(fld, item) {
            var _ref;
            return item != null ? (_ref = item._u) != null ? _ref.hasOwnProperty(fld) : void 0 : void 0;
          });
          $scope.vu = (function(fld, item) {
            var _ref;
            return (item != null ? item.hasOwnProperty(fld) : void 0) || ((_ref = item._u) != null ? _ref.hasOwnProperty(fld) : void 0);
          });
          $scope.fv = (function(form, item) {
            var k, _ref;
            if (form && item) {
              for (k in item._u) {
                if ((_ref = form[k]) != null ? _ref.$invalid : void 0) {
                  return false;
                }
              }
            }
            return true;
          });
        }
      };
    })
  ]);

}).call(this);
