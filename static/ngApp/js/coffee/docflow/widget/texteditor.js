(function() {
  var module;

  module = angular.module('docflow.widget.texteditor', ['docflow.config']);

  module.directive('docflowWidgetTexteditor', [
    (function() {
      return {
        restrict: 'A',
        require: 'ngModel',
        link: (function($scope, element, attrs, model) {
          var params;
          if (attrs.docflowWidgetTexteditor) {
            params = $scope.$eval(attrs.docflowWidgetTexteditor);
          }
          if (angular.isDefined(params) && params.mode === 'air') {
            element.redactor({
              air: true,
              deniedTags: ['html', 'head', 'link', 'body', 'meta', 'applet'],
              boldTag: 'strong',
              italicTag: 'i',
              changeCallback: (function() {
                model.$setViewValue(element.html());
                if (!$scope.$$phase) {
                  $scope.$apply();
                }
              })
            });
          } else {
            element.redactor({
              minHeight: 350,
              autoresize: false,
              convertDivs: false,
              removeEmptyTags: false,
              cleanFontTag: false,
              convertLinks: false,
              imageUpload: '/uploadImage',
              uploadFields: {
                id: (function() {
                  return $scope.item.id;
                })
              },
              deniedTags: ['html', 'head', 'link', 'body', 'meta', 'applet'],
              boldTag: 'strong',
              italicTag: 'i',
              plugins: ['fullscreen'],
              changeCallback: (function() {
                model.$setViewValue(element.html());
                if (!$scope.$$phase) {
                  $scope.$apply();
                }
              })
            });
          }
          model.$render = (function() {
            element.html(model.$viewValue);
          });
          $scope.$on('$destroy', (function() {
            var err;
            try {
              element.redactor("destroy");
            } catch (_error) {
              err = _error;
            }
          }));
        })
      };
    })
  ]);

}).call(this);
