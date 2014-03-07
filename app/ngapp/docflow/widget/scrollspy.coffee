module = angular.module 'docflow.widget.scrollspy', ['docflow.config']

module.directive 'docflowWidgetScrollspy',
['$timeout', '$window', (($timeout, $window) ->
  restrict: 'A'
  link: ($scope, element, attrs) ->

    options = if attrs.docflowWidgetScrollspy then $scope.$eval attrs.docflowWidgetScrollspy else {}

    $timeout -> element.scrollspy(options)
#    $timeout -> $('body').scrollspy(options)  ### scrollspy() doesn't have methods to be removed.  so it should be used on inner divs only!

    return # End of 'link:'

)] # End of directive 'docflowWidgetDate'
