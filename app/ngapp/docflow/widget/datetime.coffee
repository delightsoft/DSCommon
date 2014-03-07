module = angular.module 'docflow.widget.datetime', ['docflow.config']

module.directive 'docflowWidgetDate',
[(->
  restrict: 'E'
  require: 'ngModel'
  link: ($scope, element, attrs, model) ->

    # TODO: Localize

    input = $(element.children()[0])

    if attrs.options
      params = $scope.$eval attrs.options

    options =
#      format: 'yyyy-mm-dd'
      format: 'dd.mm.yyyy'
      clearBtn: true
      autoclose: true


    if angular.isObject params
      angular.extend options, params

    input.datepicker options

    model.$render = ->
      if angular.isNumber model.$viewValue
        input.datepicker 'setUTCDate', new Date(model.$viewValue)
      else
        input.val ''
      return

    input.on 'clearDate', ->
      model.$setViewValue null
      $scope.$apply()
      return

    input.on 'changeDate', ->
      model.$setViewValue (input.datepicker 'getUTCDate').getTime()
      $scope.$apply()
      return

    return # End of 'link:'

)] # End of directive 'docflowWidgetDate'

module.directive 'docflowWidgetDateTime',
[(->
#  template: '<input type="text" />'
  restrict: 'E'
  require: 'ngModel'
  link: ($scope, element, attrs, model) ->

    input = $(element.children()[0])


    if attrs.options
      params = $scope.$eval attrs.options

    options =
      format: 'hh:ii dd-mm-yy'

    if angular.isObject params
      angular.extend options, params

    input.datetimepicker options

    model.$render = ->
      if angular.isNumber model.$viewValue
        input.datetimepicker 'update', new Date(model.$viewValue)
      else
        input.val ''
      return

    input.on 'changeDate', (ev) ->
      model.$setViewValue ev.date.getTime() + 60000 * ev.date.getTimezoneOffset()
      $scope.$apply()
      return

    input.on 'change', ->
      if input.val().trim() == ''
        model.$setViewValue null
        $scope.$apply()
      return

    $('.datetimepicker-dropdown-bottom-right').css('margin-bottom','55px')

    return # End of 'link:'

)] # End of directive 'docflowWidgetDateTime'
