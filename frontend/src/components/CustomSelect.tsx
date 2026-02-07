import React, { useState, useRef, useEffect } from 'react'

type Option = {
    value: string
    label: string
}

type CustomSelectProps = {
    value: string
    onChange: (value: string) => void
    options: Option[]
    placeholder?: string
    disabled?: boolean
    className?: string
}

export default function CustomSelect({
    value,
    onChange,
    options,
    placeholder = '선택하세요',
    disabled = false,
    className = '',
}: CustomSelectProps) {
    const [isOpen, setIsOpen] = useState(false)
    const [highlightedIndex, setHighlightedIndex] = useState(-1)
    const containerRef = useRef<HTMLDivElement>(null)
    const listRef = useRef<HTMLUListElement>(null)

    const selectedOption = options.find(opt => opt.value === value)
    const displayText = selectedOption ? selectedOption.label : placeholder

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
                setIsOpen(false)
            }
        }

        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside)
            return () => document.removeEventListener('mousedown', handleClickOutside)
        }
    }, [isOpen])

    useEffect(() => {
        if (isOpen && listRef.current && highlightedIndex >= 0) {
            const highlightedElement = listRef.current.children[highlightedIndex] as HTMLElement
            if (highlightedElement) {
                highlightedElement.scrollIntoView({ block: 'nearest' })
            }
        }
    }, [highlightedIndex, isOpen])

    const handleSelect = (optionValue: string, e?: React.MouseEvent) => {
        e?.preventDefault()
        e?.stopPropagation()
        onChange(optionValue)
        setIsOpen(false)
        setHighlightedIndex(-1)
    }

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (disabled) return

        switch (e.key) {
            case 'Enter':
            case ' ':
                e.preventDefault()
                if (isOpen && highlightedIndex >= 0) {
                    handleSelect(options[highlightedIndex].value)
                } else {
                    setIsOpen(!isOpen)
                }
                break
            case 'ArrowDown':
                e.preventDefault()
                if (!isOpen) {
                    setIsOpen(true)
                } else {
                    setHighlightedIndex(prev => 
                        prev < options.length - 1 ? prev + 1 : prev
                    )
                }
                break
            case 'ArrowUp':
                e.preventDefault()
                if (isOpen) {
                    setHighlightedIndex(prev => prev > 0 ? prev - 1 : 0)
                }
                break
            case 'Escape':
                e.preventDefault()
                setIsOpen(false)
                setHighlightedIndex(-1)
                break
        }
    }

    return (
        <div 
            ref={containerRef}
            className={`relative ${className}`}
        >
            <button
                type="button"
                onClick={(e) => {
                    if (!disabled) {
                        e.stopPropagation()
                        setIsOpen(!isOpen)
                    }
                }}
                onKeyDown={handleKeyDown}
                disabled={disabled}
                className={`
                    w-full px-4 py-3 pr-10 border border-gray-200 rounded-xl
                    focus:ring-2 focus:ring-black focus:border-transparent
                    transition-all duration-200 bg-white text-left
                    flex items-center justify-between
                    ${disabled 
                        ? 'bg-gray-50 text-gray-400 cursor-not-allowed' 
                        : 'cursor-pointer hover:border-gray-300'
                    }
                    ${isOpen ? 'ring-2 ring-black border-transparent' : ''}
                `}
                aria-haspopup="listbox"
                aria-expanded={isOpen}
            >
                <span className={selectedOption ? 'text-gray-900' : 'text-gray-400'}>
                    {displayText}
                </span>
                <svg
                    className={`w-5 h-5 text-gray-500 transition-transform duration-200 ${
                        isOpen ? 'rotate-180' : ''
                    }`}
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
            </button>

            {isOpen && !disabled && (
                <div 
                    className="absolute z-50 w-full mt-2 bg-white border border-gray-200 rounded-xl shadow-2xl max-h-64 overflow-hidden animate-fade-in"
                    onMouseDown={(e) => e.preventDefault()}
                >
                    <ul
                        ref={listRef}
                        role="listbox"
                        className="overflow-y-auto max-h-64 scrollbar-thin scrollbar-thumb-gray-300 scrollbar-track-gray-100"
                    >
                        {options.map((option, index) => {
                            const isSelected = option.value === value
                            const isHighlighted = index === highlightedIndex

                            return (
                                <li
                                    key={option.value}
                                    role="option"
                                    aria-selected={isSelected}
                                    onClick={(e) => handleSelect(option.value, e)}
                                    onMouseDown={(e) => e.preventDefault()}
                                    onMouseEnter={() => setHighlightedIndex(index)}
                                    className={`
                                        px-4 py-3 cursor-pointer transition-colors
                                        ${isSelected 
                                            ? 'bg-black text-white font-medium' 
                                            : isHighlighted
                                            ? 'bg-gray-100 text-gray-900'
                                            : 'text-gray-700 hover:bg-gray-50'
                                        }
                                    `}
                                >
                                    {option.label}
                                </li>
                            )
                        })}
                    </ul>
                </div>
            )}
        </div>
    )
}
