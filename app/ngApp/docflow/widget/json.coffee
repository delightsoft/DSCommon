module = angular.module 'docflow.widget.json', []

module.directive 'dfWidgetJson',
  ['$modal',
  (($modal) ->
    restrict: 'A'
    require: 'ngModel'
    link: (($scope, element, attrs, model) ->

      model.$formatters push ((value) ->
        return JSON.stringify(value, undefined, '  '))

      model.$parsers.push ((value) ->
        return JSON.parse(value))

      return)
    )]

module.directive 'dfWidgetJsonView',
  ['$modal',
   (($modal) ->
     restrict: 'A'
     scope: true
     require: 'ngModel'
     link: (($scope, element, attrs, model) ->

       model.$render = ( ->
        element.text JSON.stringify(model.$viewValue, undefined, '  ')
        return)

       return)
   )]

