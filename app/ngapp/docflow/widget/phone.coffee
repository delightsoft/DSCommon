module = angular.module 'docflow.widget.phone', ['docflow.config']

module.filter 'phone', ->
  (input)->
    number = input || ''
    if angular.isArray(number) then return


    numberEdited = number.trim().replace(/[-\s\(\)]/g, '')

    EditPhone=(num)->
      cod  = "#{num[0..1]}"
      area = "(#{num[2..4]})"
      local = "#{num[5..7]}-#{num[8...]}"
      num = "#{cod} #{area} #{local}"
      return num


    if numberEdited.length is 12 and "#{numberEdited[0..1]}" is '+7'
      EditPhone(numberEdited)
    else
      return number