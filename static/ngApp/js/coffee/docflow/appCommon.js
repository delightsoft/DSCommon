(function() {
  var module;

  module = angular.module('docflow.appCommon', ['ui.router']);

  module.config([
    '$stateProvider', '$urlRouterProvider', '$locationProvider', (function($stateProvider, $urlRouterProvider, $locationProvider) {
      $locationProvider.html5Mode(true);
    })
  ]);

  module.run([
    '$state', '$location', '$rootScope', '$anchorScroll', (function($state, $location, $rootScope, $anchorScroll) {
      $rootScope.scrollTo = (function(id, event) {
        $location.hash(id);
        $anchorScroll();
        if (event) {
          event.preventDefault();
          event.stopPropagation();
        }
      });
    })
  ]);

  module.directive('docflowPageTitle', (function() {
    return {
      restrict: 'A',
      link: function($scope, element, attrs) {
        return attrs.$observe('pageTitle', (function(title) {
          $scope.setPageTitle(title || '');
        }));
      }
    };
  }));

  module.constant('paginationConfig', {
    boundaryLinks: false,
    directionLinks: true,
    firstText: '<<',
    previousText: '<',
    nextText: '>',
    lastText: '>>'
  });

}).call(this);
