module = angular.module 'docflow.widget.password', ['docflow.config']

module.directive 'docflowWidgetPassword',
  [(->
    restrict: 'A'
    scope: true
    require: 'ngModel'
    link: ($scope, element, attrs, model) ->

      $scope.mode = 'changeButton'

      $scope.changePassword = (->

        $scope.mode = 'passwordFields'

        model.$render = (-> # reset
          $scope.pwd1 = ''
          $scope.pwd2 = ''
          $scope.mode = 'changeButton'
          return)

        $scope.pwd1 = ''
        $scope.pwd2 = ''

        updateModel = (->
          if $scope.pwd1 && $scope.pwd1 == $scope.pwd2
            model.$setViewValue $scope.pwd1
          else
            model.$setViewValue undefined
          return)

        $scope.$watch 'pwd1', updateModel
        $scope.$watch 'pwd2', updateModel

        return)
      return)
  ]

module.directive 'docflowWidgetPasswordMatch',
  [(->
    restrict: 'A'
    scope: false
    require: 'ngModel'
    link: (($scope, element, attrs, model) ->

      model.$parsers.push ((value) ->
        model.$setValidity 'passwordMatch', value == $scope.pwd1
        return value)

      return)
  )]