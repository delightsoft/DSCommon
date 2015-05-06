module = angular.module 'docflow.widget.datetime', ['docflow.config']

# Localization is taken from http://trentrichardson.com/examples/timepicker/
# Note: For some reasons timepicker sources only has timepicker localization, but date localization is missing

$.datepicker.regional['ru'] = {
  closeText: 'Закрыть',
  prevText: '<Пред',
  nextText: 'След>',
  currentText: 'Сегодня',
  monthNames: ['Январь','Февраль','Март','Апрель','Май','Июнь',
               'Июль','Август','Сентябрь','Октябрь','Ноябрь','Декабрь'],
  monthNamesShort: ['Янв','Фев','Мар','Апр','Май','Июн',
                    'Июл','Авг','Сен','Окт','Ноя','Дек'],
  dayNames: ['воскресенье','понедельник','вторник','среда','четверг','пятница','суббота'],
  dayNamesShort: ['вск','пнд','втр','срд','чтв','птн','сбт'],
  dayNamesMin: ['Вс','Пн','Вт','Ср','Чт','Пт','Сб'],
  weekHeader: 'Не',
  dateFormat: 'dd.mm.yy',
  firstDay: 1,
  isRTL: false,
  showMonthAfterYear: false,
  yearSuffix: ''
};
$.datepicker.setDefaults($.datepicker.regional['ru']);


$.timepicker.regional['ru'] = {
  timeOnlyTitle: 'Выберите время',
  timeText: 'Время',
  hourText: 'Часы',
  minuteText: 'Минуты',
  secondText: 'Секунды',
  millisecText: 'Миллисекунды',
  timezoneText: 'Часовой пояс',
  currentText: 'Сейчас',
  closeText: 'Закрыть',
  timeFormat: 'HH:mm',
  amNames: ['AM', 'A'],
  pmNames: ['PM', 'P'],
  isRTL: false
};
$.timepicker.setDefaults($.timepicker.regional['ru']);

module.directive 'docflowWidgetDate',
['$rootScope', '$timeout',
(($rootScope, $timeout) ->
  restrict: 'EA'
  require: 'ngModel'
  link: (($scope, element, attrs, model) ->
    options = {}
    if angular.isObject(params = $scope.$eval attrs.docflowWidgetDate)
      angular.extend options, params
    input = $('input', element)
    $timeout (->
      input.datepicker(options, $.datepicker.regional['ru'])
      input.change (->
        t = input.datetimepicker('getDate')
        model.$setViewValue if t then t.getTime() - t.getTimezoneOffset()*60000 else null
        $rootScope.$digest()
        return)
      (model.$render = (->
        if input.datetimepicker('getDate') != model.$viewValue
          input.datetimepicker 'setDate', if model.$viewValue then new Date(model.$viewValue + (new Date()).getTimezoneOffset()*60000) else null
        return))()
      return), 0
    return)
  )]

module.directive 'docflowWidgetTime',
['$rootScope',
(($rootScope) ->
  restrict: 'EA'
  require: 'ngModel'
  link: (($scope, element, attrs, model) ->
    options = {}
    if angular.isObject(params = $scope.$eval attrs.docflowWidgetTime)
      angular.extend options, params
    input = $('input', element)
    $timeout (->
      input.timepicker($.datepicker.regional['ru'])
      input.change (->
        t = input.datetimepicker('getDate')
        model.$setViewValue if t then t.getTime() - t.getTimezoneOffset()*60000 else null
        $rootScope.$digest()
        return)
      (model.$render = (->
        if input.datetimepicker('getDate') != model.$viewValue
          input.datetimepicker 'setDate', if model.$viewValue then new Date(model.$viewValue + (new Date()).getTimezoneOffset()*60000) else null
        return))()
      return), 0
    return))]

module.directive 'docflowWidgetDateTime',
['$rootScope', '$timeout'
(($rootScope, $timeout) ->
  restrict: 'EA'
  require: 'ngModel'
  link: (($scope, element, attrs, model) ->
    options = {}
    if angular.isObject(params = $scope.$eval attrs.docflowWidgetDateTime)
      angular.extend options, params
    input = $('input', element)
    $timeout (->
      input.datetimepicker($.datepicker.regional['ru'])
      input.change (->
        t = input.datetimepicker('getDate')
        model.$setViewValue if t then t.getTime() - (if options.local then t.getTimezoneOffset()*60000 else 0) else null
        $rootScope.$digest()
        return)
      (model.$render = (->
        if input.datetimepicker('getDate') != model.$viewValue
          input.datetimepicker 'setDate', if model.$viewValue then new Date(model.$viewValue + if options.local then (new Date()).getTimezoneOffset()*60000 else 0) else null
        return))()
      return), 0
    return))]
