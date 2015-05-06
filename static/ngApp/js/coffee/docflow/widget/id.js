(function() {
  var module;

  module = angular.module('docflow.widget.id', []);

  module.directive('docflowIdScope', [
    (function() {
      return {
        restrict: 'A',
        scope: true,
        controller: [
          '$scope', (function($scope) {
            this.scope = $scope;
          })
        ]
      };
    })
  ]);

  module.directive('docflowId', [
    (function() {
      return {
        restrict: 'A',
        require: '^?docflowIdScope',
        link: (function($scope, element, attrs, docflowIdScope) {
          if (attrs.id) {
            throw new Error("Element already has 'id' attribute");
          }
          if (docflowIdScope) {
            $(element).attr('id', attrs.docflowId ? "" + attrs.docflowId + "-" + docflowIdScope.scope.$id : docflowIdScope.scope.$id);
          }
        })
      };
    })
  ]);

  module.directive('docflowFor', [
    (function() {
      return {
        restrict: 'A',
        require: '^?docflowIdScope',
        link: (function($scope, element, attrs, docflowIdScope) {
          if (attrs["for"]) {
            throw new Error("Element already has 'for' attribute");
          }
          if (docflowIdScope) {
            $(element).attr('for', attrs.docflowFor ? "" + attrs.docflowFor + "-" + docflowIdScope.scope.$id : docflowIdScope.scope.$id);
          }
        })
      };
    })
  ]);

}).call(this);
