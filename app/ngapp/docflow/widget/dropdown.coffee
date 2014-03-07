module = angular.module 'docflow.widget.dropdown', ['docflow.config']

module.directive 'docflowWidgetDropdown',
[(->
  link: ($scope, element, attrs) ->

    element.dropdown()

    return # End of 'link:'

)] # End of directive 'infoWidget'